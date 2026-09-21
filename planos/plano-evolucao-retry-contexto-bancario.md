# Plano de evolução do RetryController para um contexto bancário

> Status: proposta técnica para refinamento e execução incremental  
> Data de criação: 2026-09-20  
> Escopo inicial: núcleo de retry, integração HTTP, idempotência, persistência, segurança,
> observabilidade, testes e migração das features existentes.

## Sumário

1. [Resumo executivo](#1-resumo-executivo)
2. [Escopo](#2-escopo)
3. [Diagnóstico do estado atual](#3-diagnóstico-do-estado-atual)
4. [Princípios e invariantes obrigatórios](#4-princípios-e-invariantes-obrigatórios)
5. [Arquitetura-alvo](#5-arquitetura-alvo)
6. [Modelo de falhas](#6-modelo-de-falhas)
7. [Semântica e perfis de operação](#7-semântica-e-perfis-de-operação)
8. [Máquina de estados](#8-máquina-de-estados)
9. [Concorrência e cancelamento](#9-concorrência-e-cancelamento)
10. [Backoff, jitter, limites e retry budget](#10-backoff-jitter-limites-e-retry-budget)
11. [Idempotência ponta a ponta](#11-idempotência-ponta-a-ponta)
12. [Persistência, process death e WorkManager](#12-persistência-process-death-e-workmanager)
13. [Rede e Ktor](#13-rede-e-ktor)
14. [Apresentação e UX](#14-apresentação-e-ux)
15. [Observabilidade e auditoria](#15-observabilidade-e-auditoria)
16. [Proteções contra sobrecarga](#16-proteções-contra-sobrecarga)
17. [Estratégia de testes](#17-estratégia-de-testes)
18. [Plano de implementação por fases](#18-plano-de-implementação-por-fases)
19. [Sequência sugerida de PRs](#19-sequência-sugerida-de-prs)
20. [Estratégia de compatibilidade e migração](#20-estratégia-de-compatibilidade-e-migração)
21. [Critérios globais de pronto](#21-critérios-globais-de-pronto)
22. [Riscos do plano e mitigação](#22-riscos-do-plano-e-mitigação)
23. [Decisões que precisam de alinhamento externo](#23-decisões-que-precisam-de-alinhamento-externo)
24. [Referências técnicas](#24-referências-técnicas)
25. [Resultado esperado](#25-resultado-esperado)

## 1. Resumo executivo

O `RetryController` atual é uma boa implementação didática para requisições one-shot ligadas ao
ciclo de vida de uma tela. Ele já possui estado imutável, orçamento de tentativas, backoff
injetável, proteção contra double tap sequencial, cancelamento cooperativo, descarte de resultados
obsoletos e testes determinísticos.

Para uso bancário, entretanto, retry não pode ser tratado apenas como "executar novamente após uma
falha". Uma solução robusta precisa responder com segurança a quatro perguntas:

1. A requisição chegou ao servidor?
2. O servidor iniciou ou concluiu o efeito financeiro?
3. É seguro repetir exatamente essa operação?
4. Como recuperar o resultado se o aplicativo ou dispositivo encerrar durante a execução?

O objetivo deste plano é evoluir a solução para garantir invariantes de negócio, e não prometer
ausência total de falhas — algo impossível em uma comunicação distribuída. A arquitetura final
deve garantir:

- ausência de efeitos financeiros duplicados;
- repetição somente quando a semântica da operação permitir;
- payload e identidade da operação imutáveis entre tentativas;
- tempo de execução limitado por tentativa e por operação;
- resultado final confirmado ou explicitamente classificado como indeterminado;
- reconciliação de operações cujo resultado não seja conhecido pelo cliente;
- continuidade de comandos críticos após process death, quando aplicável;
- cancelamento correto sem confundir cancelamento local com reversão no servidor;
- classificação estável e testável de falhas;
- observabilidade e auditoria sem vazamento de dados sensíveis;
- proteção contra tempestades de retry e sobrecarga do backend;
- evolução gradual das telas atuais, sem uma migração big bang.

Este plano deliberadamente separa três responsabilidades:

- `RetryExecutor`: executa tentativas e aplica políticas técnicas;
- `OperationController`: serializa comandos e expõe a máquina de estados para a apresentação;
- infraestrutura durável: persiste, retoma e reconcilia comandos críticos.

O `OperationController` não substitui persistência, idempotência no servidor ou reconciliação.

---

## 2. Escopo

### 2.1 Incluído

- revisão e evolução dos contratos em `retry/`;
- taxonomia tipada de falhas;
- decisão de retry baseada na falha e na semântica da operação;
- backoff, jitter, `Retry-After`, limites e deadlines;
- timeouts explícitos no Ktor;
- concorrência e serialização dos comandos;
- idempotência ponta a ponta;
- representação de resultado indeterminado;
- persistência e retomada de operações críticas;
- integração opcional com WorkManager;
- circuit breaker, bulkhead e retry budget;
- observabilidade técnica e trilha de auditoria;
- segurança de rede e de logs;
- estratégia completa de testes;
- migração incremental de Profile, Orders, Picker e Create Order;
- critérios de aceite, rollout e rollback.

### 2.2 Fora do escopo inicial

- definição dos SLOs finais sem participação de produto, backend e SRE;
- escolha definitiva da plataforma de métricas, tracing e feature flags;
- desenho contábil do backend ou ledger financeiro;
- compensação financeira/Saga específica de um produto;
- certificate pinning sem avaliação do processo de rotação de certificados;
- transformação de polling, WebSocket ou streams infinitos em casos suportados pelo mesmo
  controller;
- garantia genérica de exactly-once. O alvo prático será efeito at-most-once por identidade de
  operação, acompanhado de reconciliação.

---

## 3. Diagnóstico do estado atual

### 3.1 Pontos fortes que devem ser preservados

- `RetryUiState` é uma hierarquia selada e impede boa parte dos estados inválidos.
- O estado público é um `StateFlow` somente leitura.
- `RetryPolicy` é injetável e facilmente substituível nos testes.
- `ExponentialBackoffPolicy` injeta `Random`, permitindo resultados determinísticos.
- O controller troca o estado para `Loading` sincronamente antes de iniciar a coroutine, evitando
  consumo duplo do orçamento em double tap sequencial.
- `generation` impede que uma execução antiga publique estado depois de ter sido substituída.
- `Flow.catch` preserva cancelamento, e há teste de regressão para o caso.
- `ApiCall` é lazy: o trabalho somente acontece na coleta.
- A chamada one-shot é validada com `.single()`; Flow vazio ou com cardinalidade incorreta falha.
- `CreateOrderViewModel` preserva o request submetido em vez de ler diretamente o formulário vivo.
- O create-order reutiliza a mesma idempotency key entre tentativas.
- O mock server retorna a resposta previamente associada à idempotency key.
- Os testes usam virtual time e não dependem de espera real.
- O projeto não oculta exceções nos repositórios.

### 3.2 Lacunas prioritárias

#### P0 — segurança funcional

- Apenas HTTP 422 é terminal; 400, 401, 403, 404, falhas de parsing e falhas desconhecidas ainda
  podem ser apresentadas como retryable.
- Não há estado para "o servidor pode ter concluído, mas o cliente não sabe".
- `job.cancel()` não garante que o efeito remoto tenha parado ou sido revertido.
- `generation` protege apenas a publicação do estado, não efeitos duplicados no servidor.
- Uma nova chamada a `load()` cancela logicamente a anterior e também reinicia o orçamento, sem
  levar em consideração a segurança da operação.
- `NewOrderRequest.idempotencyKey` aceita string vazia, permitindo uma mutação sem proteção.
- A chave e o estado da operação vivem somente na memória do ViewModel.
- Não há timeout explícito por tentativa nem deadline global.
- Não há consulta/reconciliação para operações com resultado indeterminado.

#### P1 — robustez e arquitetura

- `ErrorTypeStrategy` mistura classificação técnica, retryability, texto e ação da UI.
- Textos de erro estão hardcoded em inglês no núcleo de retry.
- `canRetry: Boolean` não representa ações como autenticar, editar, consultar status ou contatar
  suporte.
- `maxRetries` e `maxAttempts` são usados como sinônimos, embora tenham significados diferentes.
- Três `maxAttempts` representam a tentativa inicial mais duas repetições, não três retries.
- A API pública não documenta nem força confinamento a uma única thread.
- Campos mutáveis (`job` e `generation`) não são protegidos contra chamadas concorrentes.
- O backoff é aplicado depois do clique manual, mas o nome e a UX sugerem retry automático.
- O jitter atual varia apenas de 80% a 100% do atraso calculado.
- A política é única para todo o aplicativo; operações distintas precisam de perfis distintos.
- Informações como `Retry-After`, request ID e código de negócio são descartadas.
- Falhas durante o envio e falhas durante a espera da resposta são tratadas da mesma forma.
- Não existe observador de tentativas, duração, decisão ou resultado final.

#### P2 — escala operacional

- Não há circuit breaker.
- Não há bulkhead/limite de concorrência por serviço.
- Não há retry budget para impedir amplificação durante incidentes.
- Não há estratégia de pausa por ausência de conectividade validada.
- Não há testes de carga ou de retry storm.
- Não há rollout protegido por feature flag ou comparação de métricas entre engines.

### 3.3 Problemas adjacentes relevantes para um banco

- `LogLevel.ALL` pode registrar headers e corpos de requests/responses.
- O mock usa HTTP cleartext; isso deve ficar restrito ao ambiente local/debug.
- O release está sem minificação/shrinking, o que exige uma decisão explícita de segurança e
  distribuição.
- O Ktor pode ganhar retry próprio; habilitá-lo junto com o controller multiplicaria tentativas.
- Atualização de token precisa ser independente do orçamento de retry da operação de negócio.

---

## 4. Princípios e invariantes obrigatórios

Todas as decisões de implementação devem respeitar estes invariantes:

1. **Retry nunca muda a intenção original.** Payload, identidade da operação, usuário/conta e
   idempotency key permanecem estáveis durante toda a sessão.
2. **Mutação não é repetida sem prova de segurança.** O cliente só repete automaticamente uma
   mutação se houver contrato de idempotência forte e persistente no servidor.
3. **Cancelamento local não significa rollback remoto.** A UI pode parar de observar uma operação,
   mas o sistema continua capaz de consultar seu resultado.
4. **Resultado ambíguo é um estado de primeira classe.** Não deve ser convertido em "falha
   genérica" nem em "tente novamente".
5. **Todas as execuções são limitadas.** Deve haver timeout por tentativa, deadline total, máximo
   de tentativas e teto de backoff.
6. **A regra padrão é não repetir.** Retry exige uma decisão positiva do classificador e da
   semântica da operação.
7. **Existe um único dono das tentativas.** Ktor, repository e controller não podem repetir a mesma
   operação de maneira independente.
8. **Autenticação é tratada separadamente.** Refresh de token possui limite próprio e single-flight.
9. **Erros técnicos não vazam para a UI.** A apresentação recebe tipos estáveis e textos
   localizados, nunca mensagens cruas de exceptions.
10. **Observabilidade não contém PII, credenciais ou payload financeiro.**
11. **Estado persistente é a fonte de verdade para comandos críticos.** O ViewModel projeta esse
    estado, mas não o possui exclusivamente.
12. **A conectividade reportada pelo Android é apenas um sinal.** Ela não prova que o backend esteja
    acessível.
13. **Fallback/cache é explícito.** Dados possivelmente desatualizados devem carregar timestamp e
    indicação visual adequada; saldo antigo não pode parecer atual.
14. **Políticas são específicas por perfil de operação.** Uma regra global não deve decidir da
    mesma forma para GET, transferência, consulta eventual e sincronização.
15. **Toda transição crítica é testável e auditável.**

---

## 5. Arquitetura-alvo

```text
Composable
    │ intents / immutable state
    ▼
ViewModel
    │
    ▼
OperationController<Input, Output>
    ├── serializa comandos e aplica ConcurrencyPolicy
    ├── mantém a máquina de estados observável
    ├── captura input imutável por operationId
    └── delega execução
            │
            ▼
RetryExecutor
    ├── AttemptTimeout
    ├── OverallDeadline
    ├── FailureClassifier
    ├── RetryDecider
    ├── BackoffStrategy + Jitter
    ├── RetryBudget
    └── RetryObserver
            │
            ▼
Use case / Repository
    │ tipos de domínio ou Result tipado
    ▼
RemoteDataSource / Ktor
    ├── mapeamento HTTP/transport → RequestFailure
    ├── correlation/request IDs
    ├── autenticação single-flight
    └── sem retry escondido

Comandos críticos:

OperationController
    ├── DurableOperationStore (Room)
    ├── OperationReconciler
    └── WorkManager, quando a operação puder sobreviver à UI
```

### 5.1 `RetryExecutor`

Responsabilidade única: executar uma chamada one-shot de acordo com uma política já resolvida.

Ele deve:

- receber um snapshot imutável da operação;
- contar tentativas iniciadas e concluídas;
- aplicar timeout em cada tentativa;
- aplicar deadline global usando tempo monotônico;
- classificar a falha;
- solicitar ao `RetryDecider` a próxima ação;
- aguardar backoff cooperativamente;
- respeitar `Retry-After` dentro dos limites locais;
- notificar telemetria;
- propagar `CancellationException` sem convertê-la em falha;
- nunca conhecer Compose, Android resources ou ViewModel;
- nunca gerar uma nova idempotency key durante retry.

### 5.2 `OperationController`

Responsabilidade única: controlar uma operação observável e suas transições.

Ele deve:

- expor um único `StateFlow<OperationState<T>>`;
- receber `start(input)`, `retry()`, `cancelObservation()` e `verifyStatus()` conforme o perfil;
- serializar todos os comandos em uma mailbox/actor;
- aplicar `ConcurrencyPolicy`;
- criar uma sessão imutável por `operationId`;
- impedir double submit mesmo vindo de threads distintas;
- decidir se nova execução substitui, compartilha, enfileira ou rejeita a atual;
- garantir que resultado de sessão antiga nunca altere a sessão atual;
- publicar `OutcomeUnknown` quando a conclusão remota for ambígua;
- não depender de strings ou componentes Android.

### 5.3 `DurableOperationStore`

Necessário somente para operações que precisam sobreviver ao processo.

Ele deve:

- persistir operação e idempotency key antes da primeira chamada de rede;
- atualizar estado de forma transacional;
- suportar retomada e reconciliação;
- impedir duas execuções locais simultâneas para a mesma operação;
- permitir consulta por `operationId` e por idempotency key;
- aplicar retenção e limpeza compatíveis com auditoria e privacidade;
- armazenar o mínimo possível de dados sensíveis;
- criptografar dados locais quando exigido pela análise de risco;
- invalidar ou isolar registros corretamente em logout/troca de usuário.

### 5.4 `OperationReconciler`

Responsável por resolver operações em `Pending` ou `OutcomeUnknown`.

Ele deve:

- consultar um endpoint de status com `operationId` ou idempotency key;
- transformar o resultado em `Confirmed`, `Rejected`, `StillProcessing` ou `Unknown`;
- usar backoff e prazo próprios;
- não recriar a mutação apenas para descobrir seu resultado;
- manter trilha auditável das consultas;
- funcionar no foreground e, quando apropriado, via WorkManager.

---

## 6. Modelo de falhas

### 6.1 Tipo proposto

O nome definitivo pode variar, mas o contrato deve ser equivalente a:

```kotlin
sealed interface RequestFailure {
    data object Offline : RequestFailure
    data class Dns(val diagnosticCode: String?) : RequestFailure
    data class Tls(val diagnosticCode: String?) : RequestFailure

    data class Timeout(
        val stage: TimeoutStage,
        val outcomeCertainty: OutcomeCertainty,
    ) : RequestFailure

    data class Http(
        val statusCode: Int,
        val backendCode: String?,
        val retryAfter: Duration?,
        val requestId: String?,
    ) : RequestFailure

    data object AuthenticationRequired : RequestFailure
    data object PermissionDenied : RequestFailure
    data class Validation(val backendCode: String?) : RequestFailure
    data class Conflict(val backendCode: String?) : RequestFailure
    data class RateLimited(val retryAfter: Duration?) : RequestFailure
    data class Protocol(val diagnosticCode: String) : RequestFailure
    data class Local(val diagnosticCode: String) : RequestFailure
    data class Unknown(val diagnosticCode: String) : RequestFailure
}

enum class TimeoutStage {
    CONNECT,
    REQUEST_BODY,
    RESPONSE_HEADERS,
    RESPONSE_BODY,
    OVERALL,
}

enum class OutcomeCertainty {
    NOT_SENT,
    MAY_HAVE_REACHED_SERVER,
    SERVER_REJECTED,
}
```

O erro tipado pode manter a exception original apenas internamente para diagnóstico. A exception
não deve fazer parte do estado público, de igualdade do estado ou de mensagens exibidas.

### 6.2 Local do mapeamento

- Socket/Ktor/HTTP → `RequestFailure`: data layer.
- `RequestFailure` + semântica da operação → `RetryDecision`: retry/resilience layer.
- `RequestFailure` + contexto de negócio → mensagem e ação: presentation layer.
- Código de negócio recebido do backend deve ser mapeado explicitamente; não usar texto retornado
  pelo backend diretamente como copy.

### 6.3 Matriz inicial de decisão

Esta matriz é o default técnico. Cada operação pode ser mais restritiva, nunca mais permissiva sem
decisão explícita e teste.

| Falha | Consulta segura | Comando idempotente | Comando não idempotente | Decisão padrão |
|---|---:|---:|---:|---|
| Cancelamento da coroutine | não repetir | não repetir | não repetir | propagar silenciosamente |
| Sem rede validada | pausar/manual | pausar/reconciliar | não reenviar | aguardar sinal ou ação explícita |
| DNS temporário | sim | sim | não | retry com backoff |
| Falha de conexão antes do envio | sim | sim | depende da certeza | retry apenas se seguro |
| Timeout ao conectar | sim | sim | depende da certeza | retry apenas se `NOT_SENT` |
| Timeout após enviar body | sim | sim | não | `OutcomeUnknown` para mutação insegura |
| TLS/certificado | não | não | não | terminal + telemetria de segurança |
| HTTP 400 | não | não | não | terminal/erro de contrato ou validação |
| HTTP 401 | uma renovação de token | uma renovação de token | uma renovação de token | auth single-flight, fora do budget |
| HTTP 403 | não | não | não | terminal |
| HTTP 404 | específico da operação | específico da operação | não | default terminal |
| HTTP 408 | sim | sim | não sem idempotência | retry limitado |
| HTTP 409 | específico da operação | específico/idempotency conflict | não | default terminal |
| HTTP 422 | não | não | não | editar dados/ação de negócio |
| HTTP 425 | sim | sim | não | retry limitado quando seguro |
| HTTP 429 | sim | sim | não sem idempotência | respeitar `Retry-After` |
| HTTP 500 | sim, limitado | sim, limitado | não | retry com budget |
| HTTP 501 | não | não | não | terminal |
| HTTP 502 | sim | sim | não sem idempotência | retry com backoff |
| HTTP 503 | sim | sim | não sem idempotência | `Retry-After` + backoff |
| HTTP 504 | sim | sim | não sem idempotência | retry com backoff |
| Demais 5xx | explícito | explícito | não | default terminal |
| Erro de JSON/serialização | não | não | não | protocol failure + alerta |
| Mapper/invariante local | não | não | não | local failure + alerta |
| Exception desconhecida | não | não | não | fail closed |

### 6.4 Renovação de autenticação

- Implementar um coordenador single-flight: requests concorrentes aguardam a mesma renovação.
- Limitar a uma renovação por request lógico.
- Não consumir `maxAttempts` do retry de negócio.
- Se a renovação falhar, publicar ação `Authenticate`, não `Retry` genérico.
- Nunca registrar token ou resposta de autenticação.
- Evitar loop: request → 401 → refresh → request → 401 → refresh.

---

## 7. Semântica e perfis de operação

### 7.1 Tipos propostos

```kotlin
sealed interface OperationSafety {
    data object ReadOnly : OperationSafety

    data class IdempotentCommand(
        val operationId: OperationId,
        val idempotencyKey: IdempotencyKey,
    ) : OperationSafety

    data class NonIdempotentCommand(
        val operationId: OperationId,
        val statusVerification: StatusVerification?,
    ) : OperationSafety
}
```

Não usar `isIdempotent: Boolean`; o tipo deve carregar os dados obrigatórios de cada caso.

### 7.2 `OperationSpec`

O spec deve ser imutável e fornecido na criação/início da operação:

```kotlin
data class OperationSpec(
    val name: OperationName,
    val safety: OperationSafety,
    val maxAttempts: Int,
    val perAttemptTimeout: Duration,
    val overallDeadline: Duration,
    val backoff: BackoffStrategy,
    val concurrency: ConcurrencyPolicy,
    val offlineBehavior: OfflineBehavior,
    val automaticRetry: AutomaticRetryPolicy,
)
```

Validações obrigatórias:

- `maxAttempts >= 1`;
- timeouts positivos, finitos e coerentes;
- deadline maior ou igual ao timeout de uma tentativa;
- delay máximo menor que o deadline total;
- comando idempotente exige chave válida;
- comando não idempotente não pode ativar retry automático genérico;
- nome da operação deve ser enumerado/estável para métricas, não texto arbitrário.

### 7.3 Perfis iniciais

#### `ForegroundRead`

- reads idempotentes;
- até 3 tentativas totais, sujeito a calibração;
- timeout curto por tentativa;
- full jitter;
- `CancelPrevious` permitido;
- falha final oferece retry manual;
- dados anteriores podem ser preservados quando o produto permitir.

#### `ForegroundIdempotentCommand`

- operação identificada e persistida antes do request;
- mesma idempotency key em todas as tentativas;
- `DropWhileRunning` ou `JoinExisting`;
- não cancelar a operação remota apenas porque a tela saiu;
- resultado ambíguo aciona reconciliação;
- retry automático somente para falhas explicitamente seguras.

#### `ForegroundUnsafeCommand`

- sem retry automático;
- `DropWhileRunning`;
- timeout após envio produz `OutcomeUnknown`;
- ação primária é verificar status, nunca repetir cegamente;
- idealmente deve ser eliminado por evolução do contrato de backend.

#### `BackgroundSync`

- persistente;
- restrições de rede e energia quando aplicáveis;
- WorkManager com unique work;
- budget e backoff próprios;
- não compartilhar automaticamente o mesmo perfil da UI.

#### `Polling`

- componente separado;
- intervalo, deadline e condição de parada próprios;
- não usar `ApiCall.single()`;
- cada poll pode internamente usar uma política limitada de request.

---

## 8. Máquina de estados

### 8.1 Estado técnico proposto

```kotlin
sealed interface OperationState<out T> {
    data object Idle : OperationState<Nothing>

    data class Running(
        val operationId: OperationId,
        val attempt: Int,
        val maxAttempts: Int,
        val startedAt: Instant,
    ) : OperationState<Nothing>

    data class BackingOff(
        val operationId: OperationId,
        val nextAttempt: Int,
        val maxAttempts: Int,
        val retryAt: Instant,
        val reason: RetryReason,
    ) : OperationState<Nothing>

    data class Succeeded<T>(
        val operationId: OperationId,
        val data: T,
        val attemptsUsed: Int,
    ) : OperationState<T>

    data class Failed(
        val operationId: OperationId,
        val failure: PublicFailure,
        val recovery: RecoveryAction,
        val attemptsUsed: Int,
    ) : OperationState<Nothing>

    data class OutcomeUnknown(
        val operationId: OperationId,
        val recovery: RecoveryAction.VerifyStatus,
    ) : OperationState<Nothing>
}
```

`Instant` é útil para renderização/auditoria, mas decisões de elapsed time devem usar relógio
monotônico injetável.

### 8.2 Ações de recuperação

```kotlin
sealed interface RecoveryAction {
    data object Retry : RecoveryAction
    data object EditInput : RecoveryAction
    data object Authenticate : RecoveryAction
    data class VerifyStatus(val operationId: OperationId) : RecoveryAction
    data object ContactSupport : RecoveryAction
    data object Leave : RecoveryAction
}
```

O botão e seu texto passam a ser consequência da ação, e não de `canRetry: Boolean`.

### 8.3 Transições permitidas

| Estado atual | Evento | Próximo estado | Observação |
|---|---|---|---|
| Idle | Start | Running(1) | captura snapshot e operationId |
| Running | Success | Succeeded | terminal para a sessão |
| Running | RetryableFailure | BackingOff | somente decisão positiva |
| Running | TerminalFailure | Failed | não gastar novas tentativas |
| Running | AmbiguousMutation | OutcomeUnknown | iniciar reconciliação |
| BackingOff | DelayElapsed | Running(next) | verificar deadline antes |
| BackingOff | DeadlineExceeded | Failed | timeout global |
| BackingOff | NewStart | depende da concurrency policy | nunca implícito |
| Failed | ManualRetry | Running | mesma operação quando aplicável |
| OutcomeUnknown | Verify | Running/estado de verificação | não repete mutação |
| OutcomeUnknown | ReconciledSuccess | Succeeded | resultado vem do status |
| OutcomeUnknown | ReconciledFailure | Failed | resultado confirmado |
| Succeeded | NewStart | nova sessão | nova intenção e nova chave |

Transições não listadas devem ser recusadas, ignoradas com métrica ou tratadas explicitamente.

### 8.4 Contagem correta

- `attempt`: tentativa total atual, começando em 1.
- `attemptsUsed`: total de tentativas já iniciadas/concluídas conforme contrato documentado.
- `maxAttempts`: inclui a tentativa inicial.
- `retriesUsed = max(0, attemptsUsed - 1)` apenas como propriedade derivada, se a UI precisar.
- Remover alias `DEFAULT_MAX_RETRIES` ou fazê-lo representar semanticamente retries adicionais.
- Padronizar testes e textos para não chamar três attempts de três retries.

---

## 9. Concorrência e cancelamento

### 9.1 Serialização de comandos

O controller deve receber comandos por um `Channel<Command>` processado por uma única coroutine.
Isso cria ordem total para:

- start;
- manual retry;
- cancelamento;
- reconciliação;
- expiração de delay/deadline;
- resultado de attempt.

Alternativa: funções `suspend` protegidas por `Mutex`. O actor é preferível quando resultados de
jobs e comandos externos precisam participar da mesma máquina de estados.

### 9.2 Políticas de concorrência

```kotlin
sealed interface ConcurrencyPolicy {
    data object CancelPrevious : ConcurrencyPolicy
    data object DropWhileRunning : ConcurrencyPolicy
    data object JoinExisting : ConcurrencyPolicy
    data object Queue : ConcurrencyPolicy
    data object Reject : ConcurrencyPolicy
}
```

Regras:

- `CancelPrevious` somente para operações em que cancelar observação/execução seja seguro.
- `DropWhileRunning` evita double submit; deve produzir feedback acessível quando necessário.
- `JoinExisting` exige uma chave de equivalência da operação.
- `Queue` precisa de limite, política de expiração e persistência se for crítica.
- `Reject` deve retornar motivo estável ao chamador.

### 9.3 Snapshot imutável

Substituir closures que leem `lastSubmittedRequest`/`itemToSend` mutáveis por uma API que associe o
input à sessão:

```kotlin
controller.start(input = validatedRequest)
```

O executor recebe o input da sessão:

```kotlin
suspend fun execute(input: Input, context: AttemptContext): Output
```

Assim, nova edição ou novo submit não altera uma tentativa já criada.

### 9.4 Semântica do cancelamento

- Cancelamento antes do envio: pode encerrar com segurança.
- Cancelamento durante body/response: depende da operação; pode produzir estado pendente.
- Sair da tela: cancela coleta da UI, não necessariamente a operação crítica.
- Logout: deve seguir política explícita; não apagar silenciosamente uma operação remota pendente.
- `CancellationException` nunca é classificada como erro ou exibida como feedback.

---

## 10. Backoff, jitter, limites e retry budget

### 10.1 Estratégia recomendada

Para tentativas automáticas, começar com full jitter:

```text
capForAttempt = min(maxDelay, baseDelay × factor^(retryIndex))
delay = random(0, capForAttempt)
```

Onde:

- `retryIndex = 0` para o primeiro retry depois da tentativa inicial;
- todo cálculo é saturado para evitar overflow;
- `baseDelay`, `factor` e `maxDelay` são validados;
- delay nunca pode ser infinito;
- a política usa `Random` injetável;
- o delay final não pode ultrapassar o deadline restante.

Equal jitter ou decorrelated jitter podem ser escolhidos após teste de carga. A escolha deve ser
registrada em ADR.

### 10.2 `Retry-After`

- Suportar segundos e data HTTP.
- Considerar diferença de relógio; preferir cálculo defensivo.
- Aplicar um máximo local configurável.
- Se o valor for inválido, usar backoff local.
- Não aguardar além do deadline total.
- Registrar apenas que `Retry-After` foi usado e sua duração normalizada.

### 10.3 Retry automático versus manual

- Retry automático corrige falhas transitórias dentro de uma única execução lógica.
- Retry manual é uma decisão do usuário após feedback.
- Retry manual de comando idempotente mantém operation ID e idempotency key.
- Uma nova submissão explícita cria nova operation ID e nova key.
- A UI deve distinguir `BackingOff` automático de espera pelo usuário.
- Definir um teto total por operação para impedir que repetidos cliques manuais criem loop
  ilimitado.
- Para reads, um manual retry pode iniciar nova sessão e novo deadline.
- Para comandos, a regra depende do status persistido; nunca resetar cegamente a história.

### 10.4 Retry budget

Além de `maxAttempts`, aplicar budget por serviço/host:

- limitar a razão entre retries e requests originais em uma janela;
- negar retry quando o budget acabar, mesmo que a operação individual ainda tenha tentativas;
- publicar motivo `RetryBudgetExhausted`;
- diferenciar tráfego original de tráfego de retry nas métricas;
- não usar um contador global que misture hosts ou produtos independentes.

---

## 11. Idempotência ponta a ponta

### 11.1 Cliente

- Criar `IdempotencyKey` como tipo obrigatório e não vazio.
- Gerar com fonte adequada de unicidade; UUID v4 é aceitável se aprovado pela arquitetura.
- Não derivar a chave de CPF, conta, telefone ou outro identificador pessoal.
- Gerar a chave uma vez por intenção de negócio.
- Persistir chave, operation ID e fingerprint antes do primeiro envio.
- Reutilizar exatamente a mesma chave em retry, retomada e reconciliação.
- Não permitir que mapper/repository omita silenciosamente a key.
- Não colocar a key em logs de alta cardinalidade; se necessária, usar forma protegida ou um ID de
  correlação separado conforme política de segurança.

### 11.2 Backend

O contrato de produção precisa garantir:

- escopo da key por cliente/usuário/tenant;
- criação atômica do registro de idempotência e do efeito de negócio;
- persistência durável, não mapa em memória;
- fingerprint/hash dos parâmetros originais;
- mesma key + mesmo payload retorna resposta semanticamente equivalente;
- mesma key + payload diferente retorna conflito de idempotência;
- requests concorrentes com a mesma key não executam o efeito duas vezes;
- estado `PROCESSING` tem comportamento definido;
- retenção da key é maior que a janela máxima de retry/reconciliação;
- endpoint de status por operation ID;
- response inclui operation ID e request/correlation ID;
- códigos de erro são versionados e documentados;
- testes contratuais compartilhados entre cliente e backend.

### 11.3 Resultado ambíguo

Para timeout ou desconexão após possível envio:

1. não criar nova key;
2. marcar operação `OutcomeUnknown`/`PendingVerification`;
3. consultar status;
4. se o backend disser `PROCESSING`, continuar reconciliação;
5. se disser `SUCCEEDED`, publicar sucesso;
6. se disser `REJECTED`, publicar falha de negócio;
7. se não reconhecer a operação e o contrato permitir, decidir explicitamente se é seguro
   reenviar com a mesma key;
8. após o prazo de reconciliação, apresentar canal de suporte com operation ID, sem afirmar sucesso
   ou falha indevidamente.

### 11.4 Create Order como primeiro caso piloto

- Remover default vazio de `idempotencyKey`.
- Persistir a submissão antes do POST.
- Tornar o cache de idempotência do mock atomic e validar payload divergente.
- Adicionar endpoint `GET /operations/{operationId}` ou equivalente.
- Simular resposta perdida depois do commit.
- Reiniciar o processo do app e retomar consulta.
- Assegurar que apenas um pedido seja criado.

---

## 12. Persistência, process death e WorkManager

### 12.1 Quando persistir

Persistência é obrigatória quando:

- a operação pode gerar efeito financeiro;
- o resultado precisa ser conhecido mesmo após sair da tela;
- o servidor pode continuar processando depois que a UI termina;
- o usuário precisa consultar o status mais tarde;
- o trabalho pode ser retomado após process death.

Reads descartáveis de tela não precisam necessariamente desse custo.

### 12.2 Estado persistente sugerido

```text
CREATED
ENQUEUED
SENDING
PENDING_CONFIRMATION
SUCCEEDED
REJECTED
OUTCOME_UNKNOWN
EXPIRED
```

Campos mínimos:

- operation ID local;
- operation ID remoto, quando disponível;
- tipo estável da operação;
- idempotency key protegida;
- fingerprint do payload;
- payload mínimo necessário ou referência segura a ele;
- estado;
- attempts usados;
- próxima execução;
- createdAt/updatedAt/deadline;
- código de falha sanitizado;
- versão do schema;
- usuário/tenant de forma protegida e apropriada.

### 12.3 Regras transacionais

- Persistir `CREATED` antes de abrir o socket.
- Atualizar estado e attempt em transação.
- Usar compare-and-set/versão para impedir dois workers processando o mesmo item.
- Após crash em `SENDING`, retomar como `PENDING_CONFIRMATION`, não como nova operação.
- Terminal state não retorna para não terminal sem evento administrativo explícito.
- Limpeza respeita retenção, auditoria e requisitos regulatórios.

### 12.4 WorkManager

- Usar `uniqueWorkName` derivado de operation ID não sensível.
- Escolher `KEEP`/`APPEND`/`REPLACE` de acordo com a semântica, nunca por conveniência.
- Preferir `KEEP` ou `JoinExisting` para impedir workers duplicados de uma mesma operação.
- Configurar constraint de rede quando apropriado.
- Não depender do WorkManager para uma confirmação imediata na tela.
- O Worker chama o mesmo executor/use case; não duplica regra de retry.
- `Result.retry()` deve respeitar o estado persistido e não criar nova intenção.
- Conciliar o backoff do WorkManager com o do executor para evitar backoff duplo.

---

## 13. Rede e Ktor

### 13.1 Timeouts explícitos

Adicionar `HttpTimeout` com valores definidos por perfil/endpoint:

- connect timeout;
- socket timeout;
- request timeout.

Também manter deadline global no executor. Valores finais precisam ser calibrados com:

- percentis de latência reais;
- redes móveis lentas;
- tempo máximo aceitável de UX;
- comportamento do load balancer e do backend;
- margem para handshake/TLS;
- custo de falsa repetição.

Não copiar um número arbitrário para todos os endpoints.

### 13.2 Dono único dos retries

Escolher uma das opções:

1. `RetryExecutor` é o único dono, e `HttpRequestRetry` fica desabilitado; ou
2. Ktor executa retries puramente transport-level e expõe todas as tentativas ao mecanismo de
   métricas/orçamento.

Recomendação inicial: opção 1, porque o executor precisa conhecer semântica, idempotência, estado
da UI, deadline e auditoria. Retries transparentes no Ktor dificultariam a contagem real.

Também verificar/desabilitar retries automáticos do engine OkHttp quando eles conflitarem com a
política da aplicação.

### 13.3 Mapeamento de resposta

- Capturar status, `Retry-After`, request ID e código de erro estável.
- Limitar tamanho do corpo de erro.
- Validar content type esperado.
- Classificar falha de deserialização como `Protocol`, não conexão.
- Preservar cause apenas para diagnóstico interno.
- Não retornar DTO ou exception Ktor ao domínio/UI.

### 13.4 Segurança

- HTTPS obrigatório em release.
- Cleartext permitido somente para o mock/debug por Network Security Config específico.
- `LogLevel.ALL` apenas em debug e com sanitização.
- Remover `Authorization`, cookies, idempotency key, dados de conta e bodies dos logs.
- Definir política de TLS e trust anchors com o time de segurança.
- Avaliar certificate transparency e pinning com estratégia de rotação/backup pins.
- Auditar dependências e atualizar Ktor/OkHttp em ciclo controlado.
- Impedir endpoints de produção em builds de desenvolvimento quando a política exigir.

---

## 14. Apresentação e UX

### 14.1 Separação do core

- O core retorna `PublicFailure` e `RecoveryAction`.
- A UI converte isso em `@StringRes`/texto localizado.
- Nenhum texto hardcoded permanece em `retry/`.
- Nenhuma exception message aparece diretamente.

### 14.2 Estados que a UI deve distinguir

- carregamento inicial;
- refresh preservando conteúdo anterior;
- tentativa técnica em andamento;
- backoff automático com opção de cancelar observação quando permitido;
- falha retryable;
- validação que exige editar os dados;
- autenticação necessária;
- limite de tentativas atingido;
- operação ainda processando;
- resultado indeterminado em verificação;
- sucesso confirmado;
- falha de negócio confirmada.

### 14.3 Regras de comunicação

- Não exibir "falhou" quando o resultado é desconhecido.
- Não exibir "tente novamente" para transação que pode ter sido concluída.
- Mostrar um identificador de atendimento seguro quando houver resultado não reconciliado.
- Para dados em cache, exibir última atualização e estado de conectividade.
- Contagem de attempts é informação técnica; validar com UX se deve aparecer em produção.
- Acessibilidade: mudanças de estado importantes devem ser anunciadas sem provocar anúncios
  repetidos em recomposição.

---

## 15. Observabilidade e auditoria

### 15.1 Observer do executor

Definir contrato como:

```kotlin
interface RetryObserver {
    fun onOperationStarted(context: OperationTelemetryContext)
    fun onAttemptStarted(context: AttemptTelemetryContext)
    fun onAttemptFinished(result: AttemptTelemetryResult)
    fun onRetryScheduled(event: RetryScheduledEvent)
    fun onOperationFinished(result: OperationTelemetryResult)
}
```

Implementação default deve ser no-op para manter testes simples.

### 15.2 Eventos mínimos

- operação iniciada;
- attempt iniciado/concluído;
- timeout e estágio;
- falha classificada;
- decisão retry/stop/verify;
- delay escolhido e fonte local/`Retry-After`;
- retry negado por budget/deadline/circuit breaker;
- operação confirmada;
- resultado indeterminado;
- reconciliação iniciada/concluída;
- double submit descartado/joined;
- inconsistência de idempotência;
- erro de protocolo/mapper.

### 15.3 Métricas sugeridas

- taxa de sucesso na primeira tentativa;
- taxa de sucesso após retry;
- attempts por operação;
- latência por attempt e total;
- falhas por classe/status/backend code;
- operações `OutcomeUnknown`;
- tempo de reconciliação;
- idempotency conflicts;
- retries negados por budget;
- circuit breaker aberto;
- refresh de autenticação e falhas;
- operações pendentes acima do SLA.

### 15.4 Controle de cardinalidade e privacidade

- `operationName`, failure code e endpoint devem vir de enum/lista controlada.
- Nunca usar URL completa, message de exception, account ID, CPF ou operation ID como label de
  métrica.
- IDs podem existir em trace/auditoria com controle de acesso apropriado, não em métricas.
- Aplicar redaction antes de qualquer logger.
- Separar telemetria operacional de trilha de auditoria regulatória.
- Documentar retenção, acesso e finalidade de cada dado.

---

## 16. Proteções contra sobrecarga

### 16.1 Circuit breaker

- Escopo por host/serviço e, quando necessário, endpoint.
- Estados `Closed`, `Open`, `HalfOpen`.
- Abrir com base em janela e tipos de falha elegíveis, não em 4xx de cliente.
- Permitir número limitado de probes em `HalfOpen`.
- Não esconder o motivo da recusa do request.
- O breaker no cliente reduz consumo/bateria, mas não substitui proteção no backend.

### 16.2 Bulkhead

- Limitar requests simultâneos por serviço.
- Separar operações críticas de prefetch/analytics.
- Fila sempre limitada e com expiração.
- Não manter fila ilimitada em memória.
- Definir política de rejeição e prioridade.

### 16.3 Conectividade

- Usar `ConnectivityManager` apenas para antecipar que a rede não está validada.
- Revalidar sempre pela tentativa real quando apropriado.
- Pausar background sync enquanto estiver offline.
- Não disparar toda a fila imediatamente ao recuperar rede; aplicar jitter e limite de concorrência.

---

## 17. Estratégia de testes

### 17.1 Testes unitários do classificador

Cobrir no mínimo:

- IOException por estágio;
- DNS;
- TLS;
- connect/request/socket/overall timeout;
- todos os status 4xx relevantes;
- 500, 501, 502, 503, 504 e status desconhecidos;
- `Retry-After` em segundos, data, inválido, negativo e acima do teto;
- códigos de negócio;
- serialização e mapper;
- unknown failure;
- cancellation sempre propagada;
- cause preservada internamente sem vazar ao estado público.

Usar testes parametrizados/tabela para evitar lacunas na matriz.

### 17.2 Testes unitários do backoff

- tentativa inicial não tem backoff;
- índice do primeiro retry é inequívoco;
- crescimento exponencial;
- saturação no máximo;
- ausência de overflow com attempts altos;
- full/equal jitter dentro da faixa;
- random determinístico;
- delay nunca negativo, infinito ou maior que deadline restante;
- interação com `Retry-After`;
- comportamento quando o relógio avança;
- cancelamento durante delay.

### 17.3 Testes da máquina de estados

- toda transição permitida;
- toda transição proibida;
- sucesso na primeira tentativa;
- sucesso depois de N tentativas;
- falha terminal não consome tentativas adicionais;
- deadline durante request;
- deadline durante backoff;
- budget esgotado;
- breaker aberto;
- resultado indeterminado;
- reconciliação para sucesso/falha/ainda processando;
- manual retry preserva identity;
- nova submissão cria identity nova;
- resultado antigo não altera sessão nova.

Considerar testes baseados em propriedade/model-based testing para sequências aleatórias de
eventos.

### 17.4 Testes de concorrência

- `retry()` simultâneo vindo de dispatchers diferentes;
- múltiplos `start()` simultâneos;
- double tap antes de a coroutine executar;
- double tap durante request;
- start durante backoff;
- cancelamento e sucesso chegando juntos;
- API não cooperativa com cancelamento;
- `CancelPrevious`, `DropWhileRunning`, `JoinExisting`, `Queue` e `Reject`;
- apenas um efeito remoto em comando idempotente;
- nenhuma corrida em contador, generation ou state.

Usar `StandardTestDispatcher`, barriers/latches de teste e Turbine. Evitar testes dependentes de
timing real.

### 17.5 Testes com Ktor MockEngine

- headers e body preservados em retry;
- idempotency key igual em todas as tentativas;
- request/correlation IDs tratados corretamente;
- resposta perdida depois do commit;
- status + `Retry-After` capturados;
- body malformado;
- content type inesperado;
- response body lento;
- erro durante upload do body;
- autenticação refresh single-flight;
- confirmar que não existe retry oculto adicional no engine.

### 17.6 Testes contratuais do mock/backend

- mesma key + mesmo payload cria um único recurso e retorna resposta equivalente;
- mesma key + payload diferente retorna conflito;
- duas requisições concorrentes com mesma key criam um único efeito;
- cache/registro sobrevive a restart do servidor no ambiente de teste apropriado;
- operação em processamento pode ser consultada;
- `OutcomeUnknown` é reconciliado;
- retenção de key está documentada e testada;
- response inclui IDs necessários;
- códigos de erro seguem schema.

### 17.7 Testes de persistência/process death

- operação persistida antes do primeiro request;
- crash antes do envio;
- crash durante envio;
- crash depois do commit e antes da resposta;
- retomada após recriar processo/ViewModel;
- WorkManager duplicado não duplica operação;
- troca de usuário/logout;
- migração do schema Room;
- limpeza por retenção.

Parte desses testes será instrumentada; o núcleo continuará coberto por JVM tests.

### 17.8 Testes de resiliência e carga

- milhares de clientes simulados recuperando conectividade juntos;
- distribuição do jitter;
- backend retornando 429/503 com `Retry-After`;
- circuit breaker abrindo/fechando;
- retry budget contendo amplificação;
- rede lenta, perda de pacote, reset de conexão e DNS intermitente;
- latência de cauda;
- ausência de ANR e uso controlado de bateria;
- volume de métricas/logs sob incidente.

### 17.9 Testes de segurança

- build release não registra body/headers sensíveis;
- Authorization/cookies/keys são redigidos;
- cleartext bloqueado fora de debug/local;
- payload persistido não fica exposto em storage indevido;
- exceptions não aparecem na UI;
- métricas não carregam identificadores pessoais;
- dependências e configuração TLS passam pela esteira de segurança.

---

## 18. Plano de implementação por fases

Cada fase deve ser entregue em PRs pequenos. Uma fase só avança quando seus critérios de aceite
forem atendidos.

### Fase 0 — alinhamento e ADRs

**Objetivo:** remover ambiguidades antes de mudar código.

Tarefas:

- catalogar todas as operações atuais e futuras conhecidas;
- classificar cada uma como read, idempotent command, unsafe command, polling ou background sync;
- definir SLOs preliminares de latência e conclusão;
- definir quais operações sobrevivem à tela/processo;
- obter contrato do backend para idempotência e status;
- definir ownership do retry;
- definir semântica de cancelamento;
- decidir estratégia de jitter;
- decidir persistência/Room e uso de WorkManager;
- definir plataforma de métricas/tracing/feature flags;
- produzir ADRs:
  - ADR-001: owner único de retry;
  - ADR-002: taxonomia de falhas;
  - ADR-003: idempotência e resultado indeterminado;
  - ADR-004: persistência de comandos críticos;
  - ADR-005: políticas por perfil de operação;
  - ADR-006: logging e privacidade.

Critérios de aceite:

- matriz de operações aprovada por Android, backend, produto, segurança e SRE;
- nenhum comando crítico permanece sem definição de idempotência/reconciliação;
- timeouts e budgets têm responsáveis por calibração.

### Fase 1 — erros tipados e apresentação desacoplada

**Objetivo:** parar de tomar decisões a partir de `Throwable` e remover copy do core.

Tarefas:

- expandir/substituir `RequestException` por `RequestFailure` tipado;
- criar mapper Ktor/HTTP → falha;
- capturar status, backend code, request ID e `Retry-After`;
- criar `FailureClassifier`/`RetryDecider`;
- criar `RecoveryAction` selada;
- mover textos para resources/presentation mapper;
- tornar default conservador: unknown não é retryable;
- manter adapter temporário para `RetryUiState.Feedback` durante migração;
- corrigir testes de 400/401/403/404/409/422/429/5xx.

Arquivos candidatos:

- `domain/error/` ou novo pacote de failure estável;
- `data/remote/` para mapper de transport;
- `retry/ErrorTypeStrategy.kt`, a ser quebrado/substituído;
- `ui/components/` e resources de strings.

Critérios de aceite:

- nenhum texto de UI em `retry/`;
- nenhum status 4xx é retryable por default, exceto decisão explícita;
- cancellation não vira failure;
- matriz completa coberta por testes.

### Fase 2 — novo `RetryExecutor`

**Objetivo:** isolar a execução das tentativas em um componente puro.

Tarefas:

- criar `OneShotCall` suspending ou documentar rigidamente o contrato Flow one-shot;
- criar `AttemptContext`;
- criar `OperationSpec` e perfis;
- implementar timeout por tentativa;
- implementar deadline global com relógio monotônico injetável;
- implementar full jitter e teto;
- integrar `Retry-After`;
- criar `RetryDecision` selada;
- criar observer no-op;
- validar toda configuração;
- garantir que policy/classifier inválidos falhem de forma controlada e observável;
- impedir delay acima do deadline restante.

Critérios de aceite:

- executor não importa Android, Compose, Hilt ou resources;
- 100% das decisões importantes cobertas por unit tests;
- nenhuma tentativa ocorre depois do deadline;
- attempts possuem numeração inequívoca;
- cancelamento durante request/backoff é transparente.

### Fase 3 — `OperationController` serializado

**Objetivo:** eliminar corridas e representar estados completos.

Tarefas:

- criar `OperationState`;
- criar mailbox/actor de comandos;
- implementar concurrency policies necessárias inicialmente;
- mudar API para `start(input)` com snapshot imutável;
- remover dependência de vars mutáveis capturadas por closures;
- representar `BackingOff` e `OutcomeUnknown`;
- adicionar recovery actions;
- impedir double submit multi-thread;
- criar adapter para telas ainda baseadas em `RetryUiState`;
- documentar ownership/lifecycle do controller.

Critérios de aceite:

- testes concorrentes passam repetidamente;
- toda transição é determinística;
- resultado obsoleto nunca afeta sessão atual;
- comando mutável não usa `CancelPrevious` por engano;
- input de uma sessão é imutável.

### Fase 4 — rede, timeout, auth e segurança básica

**Objetivo:** tornar a borda HTTP explícita e segura.

Tarefas:

- instalar/configurar `HttpTimeout`;
- verificar retry automático do Ktor/OkHttp e definir owner único;
- implementar auth refresh single-flight;
- mapear timeouts por estágio quando tecnicamente possível;
- restringir Logging a debug;
- sanitizar headers e remover bodies sensíveis;
- separar Network Security Config debug/release;
- bloquear cleartext em release;
- adicionar correlation IDs;
- adicionar testes MockEngine.

Critérios de aceite:

- request lento termina dentro do limite definido;
- 401 não entra em loop;
- build release não registra conteúdo sensível;
- nenhuma tentativa escondida altera a contagem do executor;
- HTTP cleartext não é permitido no ambiente produtivo.

### Fase 5 — piloto em reads

**Objetivo:** validar a nova arquitetura em operações de menor risco.

Ordem sugerida:

1. Profile;
2. Orders GET;
3. Picker items.

Tarefas:

- migrar ViewModels para `OperationController`;
- manter um único `XxxUiState` por tela;
- preservar estado de sucesso durante refresh quando a UX definir;
- adaptar `RetryStateScaffold` para novos estados/actions;
- instrumentar métricas;
- comparar comportamento antigo e novo em testes;
- disponibilizar feature flag por tela se houver infraestrutura.

Critérios de aceite:

- paridade funcional;
- sem regressão de navegação/effects;
- métricas de attempts coerentes;
- cancellation e refresh cobertos;
- remoção dos adapters dessas telas.

### Fase 6 — idempotência forte no Create Order

**Objetivo:** validar comandos mutáveis com proteção ponta a ponta.

Tarefas Android:

- criar tipos `OperationId` e `IdempotencyKey`;
- remover idempotency key vazia/default;
- mudar controller para receber request snapshot;
- aplicar `DropWhileRunning` ou `JoinExisting`;
- representar `OutcomeUnknown`;
- implementar status verification;
- persistir operação antes do POST;
- retomar após recriação.

Tarefas servidor/mock:

- persistir idempotência em store apropriado ao teste;
- validar fingerprint;
- tratar concorrência com a mesma key;
- retornar mesma resposta;
- expor status;
- simular processamento, timeout após commit e restart.

Critérios de aceite:

- retries, double tap, concorrência e process restart criam exatamente um pedido;
- mesma key com payload divergente é rejeitada;
- timeout pós-commit chega a sucesso por reconciliação;
- nenhum feedback incentiva nova submissão quando o resultado é desconhecido.

### Fase 7 — durabilidade e WorkManager

**Objetivo:** tornar operações selecionadas independentes do ViewModel.

Tarefas:

- adicionar Room e schema versionado;
- implementar DAO/store transacional;
- implementar reconciler;
- criar unique Work por operation ID;
- integrar constraints;
- projetar estado persistido no ViewModel;
- tratar login/logout/troca de usuário;
- definir retenção/limpeza;
- adicionar testes de migração e instrumentados de process death.

Critérios de aceite:

- estado crítico sobrevive a process death;
- dois workers não processam a mesma operação simultaneamente;
- retomada não gera nova idempotency key;
- operação terminal não é reenviada;
- política de dados locais aprovada por segurança.

### Fase 8 — proteção de escala e observabilidade

**Objetivo:** evitar que retry amplifique incidentes e tornar comportamento auditável.

Tarefas:

- implementar retry observer;
- integrar métricas e tracing;
- configurar dashboards e alertas;
- implementar retry budget;
- implementar circuit breaker por serviço;
- adicionar bulkheads e fila limitada;
- aplicar recuperação de rede com jitter;
- criar testes de carga e caos;
- definir runbooks de incidentes.

Critérios de aceite:

- incidentes simulados não causam crescimento descontrolado de requests;
- dashboards mostram sucesso inicial, sucesso após retry, exhausted e unknown;
- breaker/budget possuem alertas e runbook;
- nenhuma dimensão de métrica contém PII ou alta cardinalidade acidental.

### Fase 9 — rollout e remoção do legado

**Objetivo:** ativar a nova solução com risco controlado.

Tarefas:

- ativar por feature flag/percentual/ambiente;
- comparar métricas por versão;
- observar crash, ANR, latência, retries e unknown outcomes;
- validar backend capacity;
- definir gatilhos objetivos de rollback;
- migrar Picker send e demais comandos;
- remover `RetryUiState`/`ErrorTypeStrategy` antigos quando não houver consumidores;
- atualizar `AGENTS.md`, diagramas e documentação operacional;
- realizar revisão final com segurança/SRE/backend.

Critérios de aceite:

- 100% dos consumidores migrados;
- nenhuma política de retry duplicada permanece;
- código legado removido;
- SLOs e alertas em produção;
- rollback testado;
- documentação e ownership publicados.

---

## 19. Sequência sugerida de PRs

1. `docs(retry): define operation profiles and failure matrix`
2. `refactor(error): introduce typed request failures`
3. `refactor(retry): separate retry decisions from presentation`
4. `feat(retry): add operation specs deadlines and full jitter`
5. `feat(retry): add pure retry executor and observer`
6. `feat(retry): add serialized operation controller`
7. `fix(network): configure explicit timeouts and safe logging`
8. `feat(auth): coordinate single-flight token refresh`
9. `refactor(profile): migrate profile to operation controller`
10. `refactor(orders): migrate order reads to operation controller`
11. `refactor(picker): migrate item reads to operation controller`
12. `feat(idempotency): require stable operation identity for create order`
13. `feat(operations): persist and reconcile critical commands`
14. `feat(work): resume pending operations with unique work`
15. `feat(resilience): add retry budgets circuit breaker and bulkheads`
16. `feat(observability): publish sanitized retry metrics and traces`
17. `test(resilience): add process-death concurrency and chaos coverage`
18. `refactor(retry): remove legacy retry controller contracts`

Cada PR deve ser pequeno o suficiente para revisão completa e não misturar mudança estrutural com
migração de todas as features.

---

## 20. Estratégia de compatibilidade e migração

- Manter o `RetryControllerFactory` atual enquanto os novos componentes são introduzidos.
- Criar adapters somente como ponte temporária, marcados com documentação de remoção.
- Migrar reads antes de mutações.
- Não alterar simultaneamente contrato de backend e todas as telas.
- Não reutilizar o mesmo estado persistido entre engine antiga e nova sem versionamento.
- Feature flag deve escolher uma única engine por operação, nunca executar as duas mutações em
  paralelo para comparação.
- Shadow mode só é aceitável para classificação/decisão sem disparar o request duplicado.
- Garantir que rollback reconheça operações criadas pela versão nova.
- Versionar o schema local e o protocolo de status antes do rollout.

---

## 21. Critérios globais de pronto

Uma implementação não deve ser considerada pronta para produção bancária até que:

- [ ] todas as operações estejam classificadas por semântica;
- [ ] retries automáticos tenham owner único;
- [ ] falhas sejam tipadas e a regra default seja fail closed;
- [ ] timeouts por attempt e deadline global estejam configurados;
- [ ] `Retry-After` seja tratado com teto;
- [ ] comandos mutáveis críticos tenham idempotência obrigatória;
- [ ] o backend garanta deduplicação atômica e validação de payload;
- [ ] exista `OutcomeUnknown` e reconciliação;
- [ ] comandos que precisam sobreviver estejam persistidos antes do envio;
- [ ] double tap e concorrência não gerem efeito duplicado;
- [ ] autenticação não entre em loop e seja single-flight;
- [ ] logs de release não contenham headers/bodies sensíveis;
- [ ] cleartext esteja bloqueado em release;
- [ ] métricas e tracing estejam sanitizados;
- [ ] circuit breaker/retry budget tenham sido avaliados e testados;
- [ ] testes de unidade, integração, contrato, concorrência e process death passem;
- [ ] testes de carga demonstrem ausência de retry storm;
- [ ] rollout e rollback tenham critérios objetivos;
- [ ] segurança, backend, SRE e produto aprovem os contratos;
- [ ] runbooks e ownership estejam documentados.

---

## 22. Riscos do plano e mitigação

| Risco | Impacto | Mitigação |
|---|---|---|
| Complexidade excessiva em reads simples | manutenção maior | perfis e componentes opcionais; persistência só onde necessário |
| Retry duplicado entre camadas | carga e duplicidade | owner único e testes de número real de requests |
| Backend sem idempotência forte | efeitos duplicados | bloquear retry automático de comandos até contrato existir |
| Estado local divergente do backend | UX incorreta | reconciler e backend como autoridade do resultado |
| Fila persistente cresce indefinidamente | storage/bateria | limite, TTL, prioridade e alertas |
| Circuit breaker bloqueia recuperação | indisponibilidade local | half-open, probes e configuração remota segura |
| Cardinalidade alta nas métricas | custo/indisponibilidade | enums e revisão de telemetria |
| Logging vaza dados | incidente de segurança | debug-only, redaction e testes de release |
| Migração big bang | regressão ampla | rollout por operação e adapters temporários |
| Feature flag duplica mutações | efeito financeiro duplicado | nunca executar engines em paralelo para comandos |
| WorkManager cria retry duplo | atraso/carga | coordenar estado persistido e apenas um backoff ativo |
| Pinning impede acesso após rotação | indisponibilidade | decisão com segurança, backup pins e processo testado |

---

## 23. Decisões que precisam de alinhamento externo

Antes das fases de comando crítico, obter resposta formal para:

1. Quais operações têm efeito financeiro ou regulatório?
2. Quais operações precisam continuar após o usuário sair da tela?
3. Qual é o SLO de conclusão e reconciliação de cada uma?
4. O backend oferece idempotência atômica? Por quanto tempo retém a key?
5. Existe endpoint de status por operation ID?
6. Como o backend diferencia `PROCESSING`, `SUCCEEDED`, `REJECTED` e `UNKNOWN`?
7. Quais códigos HTTP e códigos de negócio são contratuais?
8. Qual é a semântica de uma mesma key com payload diferente?
9. Qual informação pode ser persistida no dispositivo?
10. Quais dados podem entrar em traces e logs de auditoria?
11. Como logout e troca de usuário afetam operações pendentes?
12. Qual plataforma será usada para métricas, tracing e feature flags?
13. Quais são os gatilhos de rollback?
14. Quais ambientes permitem cleartext e logging detalhado?
15. Quem é o owner operacional do retry policy e dos runbooks?

---

## 24. Referências técnicas

- Kotlin Flow `catch` e transparência de cancelamento:
  <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/catch.html>
- Ktor `HttpRequestRetry`:
  <https://ktor.io/docs/client-request-retry.html>
- Ktor client timeout:
  <https://ktor.io/docs/client-timeout.html>
- Android WorkManager e trabalho persistente:
  <https://developer.android.com/develop/background-work/background-tasks/persistent>
- Android Network Security Configuration:
  <https://developer.android.com/privacy-and-security/security-config>
- Android Log Info Disclosure:
  <https://developer.android.com/privacy-and-security/risks/log-info-disclosure>
- AWS — Making retries safe with idempotent APIs:
  <https://aws.amazon.com/builders-library/making-retries-safe-with-idempotent-APIs/>
- AWS — Control and limit retry calls:
  <https://docs.aws.amazon.com/wellarchitected/latest/framework/rel_mitigate_interaction_failure_limit_retries.html>
- IETF draft — Idempotency-Key HTTP Header Field:
  <https://datatracker.ietf.org/doc/html/draft-ietf-httpapi-idempotency-key-header>

---

## 25. Resultado esperado

Ao final, o projeto deixará de ter apenas um controller que repete requests e passará a possuir
uma plataforma pequena de execução resiliente:

- reads simples continuam fáceis de implementar;
- comandos idempotentes são repetidos com segurança;
- comandos inseguros falham de forma conservadora;
- resultados ambíguos são reconciliados;
- operações críticas sobrevivem ao ciclo de vida da UI;
- backend e cliente compartilham um contrato explícito de idempotência;
- retry não amplifica incidentes;
- decisões são observáveis, testáveis e auditáveis;
- UI apresenta ações corretas em vez de um `canRetry` genérico;
- segurança e privacidade fazem parte do desenho, não de um ajuste posterior.

Esse é o patamar recomendado para usar o mecanismo como base de múltiplas jornadas bancárias sem
transformar um retry genérico em um ponto central de risco sistêmico.
