# Plano básico de evolução do RetryController

> Status: proposta técnica enxuta, pronta para execução incremental
> Data de criação: 2026-09-20
> Derivado de: [plano-evolucao-retry-contexto-bancario.md](plano-evolucao-retry-contexto-bancario.md)
> Escopo: erros tipados, decisão de retry, limites de tempo, concorrência, borda HTTP,
> apresentação e testes — **sem idempotência ponta a ponta e sem persistência**.

## Sumário

1. [Objetivo e diferença para o plano completo](#1-objetivo-e-diferença-para-o-plano-completo)
2. [Escopo](#2-escopo)
3. [Diagnóstico enxuto](#3-diagnóstico-enxuto)
4. [Princípios](#4-princípios)
5. [Arquitetura-alvo](#5-arquitetura-alvo)
6. [Modelo de falhas](#6-modelo-de-falhas)
7. [Decisão de retry](#7-decisão-de-retry)
8. [Perfis de operação](#8-perfis-de-operação)
9. [Máquina de estados](#9-máquina-de-estados)
10. [Backoff, jitter e limites de tempo](#10-backoff-jitter-e-limites-de-tempo)
11. [Concorrência e cancelamento](#11-concorrência-e-cancelamento)
12. [Rede e Ktor](#12-rede-e-ktor)
13. [Apresentação](#13-apresentação)
14. [Observabilidade mínima](#14-observabilidade-mínima)
15. [Estratégia de testes](#15-estratégia-de-testes)
16. [Plano de implementação por fases](#16-plano-de-implementação-por-fases)
17. [Sequência sugerida de PRs](#17-sequência-sugerida-de-prs)
18. [Critérios de pronto](#18-critérios-de-pronto)
19. [Riscos e mitigação](#19-riscos-e-mitigação)
20. [O que fica para o plano completo](#20-o-que-fica-para-o-plano-completo)

---

## 1. Objetivo e diferença para o plano completo

O plano bancário completo descreve a arquitetura necessária para operar retry sobre dinheiro:
idempotência forte, persistência de comandos, reconciliação, WorkManager, circuit breaker, retry
budget, rollout com feature flag. Ele é correto, mas é grande demais para o estágio atual do
projeto e introduz infraestrutura (Room, WorkManager, endpoint de status, contrato de backend) que
não existe.

Este plano pega o **subconjunto que traz a maior parte do ganho de robustez sem infraestrutura
nova**. Ele resolve as lacunas que hoje produzem comportamento errado com o backend que já
existe:

- decisões de retry tomadas a partir de `Throwable` genérico;
- 4xx não retryable sendo apresentado como retryable;
- ausência de timeout por tentativa e de deadline total;
- textos de UI hardcoded dentro do núcleo `retry/`;
- `canRetry: Boolean` incapaz de representar "edite os dados" ou "faça login";
- input mutável capturado por closure em vez de snapshot da sessão;
- `LogLevel.ALL` registrando headers e bodies em qualquer build;
- vocabulário ambíguo entre `attempts` e `retries`.

Tudo isso é resolvível em cinco fases pequenas, sem tocar em banco de dados local, sem
background work e sem mudar o contrato do servidor.

### 1.1 Decisão explícita sobre idempotência

O projeto já tem uma implementação **básica** de idempotência: `CreateOrderViewModel` gera um
`UUID` por submissão, `OrdersRemoteDataSource` o envia como header `Idempotency-Key`, e o mock
server guarda a resposta em um `Map` em memória.

**Este plano não mexe nisso.** Não tipa a chave, não valida fingerprint de payload, não trata
conflito de payload divergente, não persiste a chave e não cria endpoint de status. O que já
funciona continua funcionando; o que falta fica registrado na seção 20.

A consequência arquitetural precisa ficar explícita: **sem idempotência forte comprovada, o
cliente não repete automaticamente uma mutação**. Este plano trata isso como invariante, não como
limitação temporária — é o que permite entregar segurança real sem construir a infraestrutura
pesada.

---

## 2. Escopo

### 2.1 Incluído

- taxonomia tipada de falhas (`RequestFailure`), substituindo classificação por `Throwable`;
- separação entre classificação técnica, decisão de retry e copy da UI;
- `RecoveryAction` selada no lugar de `canRetry: Boolean`;
- textos movidos para `strings.xml`, núcleo `retry/` sem copy;
- timeout por tentativa e deadline global com relógio monotônico injetável;
- full jitter e teto de backoff;
- suporte a `Retry-After`;
- perfis de operação (read vs. command), sem retry automático de mutação;
- snapshot imutável de input por sessão (`start(input)`);
- serialização de comandos do controller e política de concorrência explícita;
- `HttpTimeout` no Ktor, owner único de retry e logging seguro por build type;
- observer no-op para tentativas e decisões;
- padronização de `attempts` vs. `retries`;
- migração das telas existentes.

### 2.2 Explicitamente fora

| Fora | Motivo |
|---|---|
| Evolução da idempotency key (tipo, fingerprint, conflito, retenção) | pedido explícito; a versão básica atual é suficiente para o estudo |
| Persistência local (Room, store durável, retomada) | exige schema, migração e política de dados |
| WorkManager e continuidade após process death | depende de persistência |
| `OutcomeUnknown` com reconciliação e endpoint de status | depende de contrato de backend |
| Circuit breaker, bulkhead, retry budget por host | só faz sentido com tráfego real |
| Auth refresh single-flight | não há autenticação no projeto |
| Certificate pinning, ADRs formais, feature flags, SLOs, testes de carga/caos | dependem de times externos |

Onde a ausência de uma dessas peças mudar uma decisão técnica, o plano diz qual comportamento
conservador adotar no lugar.

---

## 3. Diagnóstico enxuto

### 3.1 O que já está certo e deve ser preservado

- `RetryUiState` é selado e impede boa parte dos estados inválidos.
- O estado público é um `StateFlow` somente leitura, um por tela.
- `RetryPolicy` é injetável; `ExponentialBackoffPolicy` recebe `Random`.
- O controller publica `Loading` **sincronamente** antes de lançar a coroutine, o que fecha a
  janela de double tap sequencial.
- `generation` impede que uma execução superada publique estado.
- `Flow.catch` preserva cancelamento, com teste de regressão dedicado.
- `ApiCall` é lazy e validado com `.single()`.
- `CreateOrderViewModel` captura `lastSubmittedRequest` no submit, e não lê o formulário vivo.
- Testes usam virtual time; nenhum depende de espera real.

### 3.2 Lacunas que este plano resolve

#### Alta prioridade

- `DefaultErrorTypeStrategy` só trata 422 como terminal. **400, 401, 403, 404 e 409 aparecem como
  retryable**, e repetir qualquer um deles é garantidamente inútil.
- Falha de parsing/serialização cai em `GENERIC` e vira retryable; repetir não conserta contrato.
- Exception desconhecida é retryable por default — a regra deveria ser fail closed.
- Não existe timeout por tentativa nem deadline global: uma resposta que nunca chega prende a tela
  indefinidamente, e o `HttpClient` não instala `HttpTimeout`.
- `ApiClient` traduz toda `IOException` em `RequestException.Connection`, sem distinguir "não
  enviou" de "enviou e não sei a resposta" — o que decide se repetir uma mutação é seguro.
- `NetworkModule` usa `LogLevel.ALL` incondicionalmente, registrando headers e bodies em release.

#### Média prioridade

- `ErrorTypeStrategy` mistura quatro responsabilidades: classificar, decidir retryability,
  produzir título/descrição e definir o label do botão.
- Os textos estão hardcoded em inglês dentro de `retry/`, sem `strings.xml` e sem localização.
- `canRetry: Boolean` não expressa "editar dados", "voltar" ou "falar com suporte"; hoje o 422
  depende de um `if` sobre esse booleano para trocar o label.
- `RetryControllerFactory` expõe `DEFAULT_MAX_ATTEMPTS` **e** um alias `DEFAULT_MAX_RETRIES` com o
  mesmo valor, e `create(maxRetries = ...)` alimenta o parâmetro `maxAttempts`. Três attempts
  viram "três retries" na conversa e nos testes.
- `createWithErrorStrategy` duplica `create` só para trocar a estratégia; um único método com
  parâmetro default resolve.
- O backoff é aplicado **depois** do clique manual em "Try again", mas nada na UI comunica a
  espera — o usuário vê um `Loading` sem explicação.
- O jitter atual varia de 80% a 100% do delay, faixa estreita demais para dispersar clientes.
- Retry sempre reexecuta a mesma closure; para comandos, isso depende de a closure ler um snapshot
  (hoje garantido por convenção, não pelo tipo).
- `job` e `generation` são `var` sem proteção; a API não documenta confinamento a uma thread.
- Nenhuma visibilidade sobre tentativas, duração ou decisão tomada.

---

## 4. Princípios

1. **A regra padrão é não repetir.** Retry exige decisão positiva do classificador *e* do perfil
   da operação. Falha desconhecida é terminal.
2. **Mutação não é repetida automaticamente.** Sem idempotência forte comprovada, só o usuário
   decide reenviar, e apenas quando a falha prova que nada chegou ao servidor.
3. **Retry nunca muda a intenção original.** O input da sessão é capturado no start e é imutável.
4. **Toda execução é limitada.** Timeout por tentativa, deadline total, máximo de tentativas e
   teto de backoff — todos obrigatórios.
5. **Existe um único dono das tentativas.** Ktor, repository e controller não repetem de forma
   independente.
6. **Erros técnicos não vazam para a UI.** A apresentação recebe tipos estáveis e strings
   localizadas, nunca `exception.message`.
7. **Cancelamento não é falha.** `CancellationException` é propagada, nunca classificada.
8. **Logs não contêm headers, bodies ou credenciais em release.**

---

## 5. Arquitetura-alvo

```text
Composable
    │ intents / immutable state
    ▼
ViewModel
    │ start(input) / retry() / cancel()
    ▼
RetryController<Input, Output>
    ├── serializa comandos (Mutex ou Channel)
    ├── captura snapshot imutável do input
    ├── mantém um StateFlow<RetryUiState<Output>>
    └── delega execução
            │
            ▼
RetryExecutor
    ├── OperationSpec (maxAttempts, timeouts, backoff, concorrência)
    ├── timeout por tentativa + deadline global (TimeSource injetável)
    ├── FailureClassifier → RequestFailure
    ├── RetryDecider     → RetryDecision
    ├── BackoffStrategy (full jitter) + Retry-After
    └── RetryObserver (no-op por padrão)
            │
            ▼
Use case / Repository
    │ tipos de domínio, Flow one-shot
    ▼
RemoteDataSource / ApiClient / Ktor
    ├── mapeia transporte/HTTP → RequestFailure
    ├── HttpTimeout configurado
    └── sem retry escondido
```

Comparado ao plano completo, some a coluna durável (`DurableOperationStore`,
`OperationReconciler`, WorkManager) e os componentes de escala (breaker, bulkhead, budget). O que
fica é a separação que realmente paga em legibilidade e testabilidade: **executor puro** (decide e
executa tentativas) versus **controller observável** (serializa comandos e publica estado).

### 5.1 `RetryExecutor`

Responsabilidade única: executar uma chamada one-shot segundo uma política já resolvida.

- recebe input imutável e `OperationSpec`;
- aplica timeout por tentativa e deadline global (tempo monotônico);
- classifica a falha e pede a decisão ao `RetryDecider`;
- aguarda backoff de forma cooperativa, respeitando o deadline restante;
- notifica o observer;
- propaga `CancellationException` sem convertê-la;
- não conhece Compose, Android resources, Hilt ou ViewModel.

### 5.2 `RetryController`

Responsabilidade única: controlar uma operação observável e suas transições.

- expõe um único `StateFlow<RetryUiState<T>>`;
- recebe `start(input)`, `retry()` e `cancel()`;
- serializa comandos, para que double tap vindo de dispatchers distintos não crie corrida;
- aplica a `ConcurrencyPolicy` do perfil;
- garante que resultado de sessão antiga não altere a sessão atual (evolução do `generation`
  atual);
- não depende de strings nem de componentes Android.

O tipo ganha um segundo parâmetro (`RetryController<Input, Output>`). Para reads sem input, o
alias `RetryController<Unit, T>` mantém as telas simples.

---

## 6. Modelo de falhas

### 6.1 Tipo proposto

`RequestException` (hoje `Connection` + `Http`) é substituído por um tipo de dados, não uma
exception, no limite entre data e retry:

```kotlin
sealed interface RequestFailure {
    data object Offline : RequestFailure

    data class Timeout(
        val stage: TimeoutStage,
        val certainty: OutcomeCertainty,
    ) : RequestFailure

    data class Connection(val certainty: OutcomeCertainty) : RequestFailure

    data class Http(
        val statusCode: Int,
        val retryAfter: Duration?,
        val requestId: String?,
    ) : RequestFailure

    data class Protocol(val diagnosticCode: String) : RequestFailure   // JSON, content type, mapper
    data class Unknown(val diagnosticCode: String) : RequestFailure
}

enum class TimeoutStage { CONNECT, REQUEST_BODY, RESPONSE, OVERALL }

enum class OutcomeCertainty {
    /** O request comprovadamente não saiu: seguro repetir qualquer operação. */
    NOT_SENT,
    /** Pode ter chegado ao servidor: só repetir se a operação for read ou idempotente. */
    MAY_HAVE_REACHED_SERVER,
}
```

Simplificações deliberadas em relação ao plano completo: `Dns` e `Tls` entram em `Connection` com
`diagnosticCode`; `AuthenticationRequired`, `PermissionDenied`, `Validation`, `Conflict` e
`RateLimited` são derivados do `statusCode` pelo classificador em vez de virarem casos próprios —
o projeto não tem autenticação nem códigos de negócio para justificar o desdobramento.

`OutcomeCertainty` é a peça mais importante e a que está faltando hoje: é ela que substitui a
idempotência como critério de segurança. Sem chave forte, `MAY_HAVE_REACHED_SERVER` em uma mutação
significa **parar**, não repetir.

A exception original é preservada apenas como `cause` interna para diagnóstico. Ela não entra no
estado público, na igualdade do estado nem em texto exibido.

### 6.2 Onde cada mapeamento vive

| Transformação | Camada |
|---|---|
| Socket / Ktor / HTTP → `RequestFailure` | `data/remote` (evolução do `ApiClient`) |
| `RequestFailure` + perfil → `RetryDecision` | `retry/` |
| `RequestFailure` + `RecoveryAction` → texto | `ui/` + `strings.xml` |

---

## 7. Decisão de retry

Matriz default. Um perfil pode ser mais restritivo, nunca mais permissivo sem teste dedicado.

| Falha | Read | Command | Observação |
|---|---|---|---|
| `CancellationException` | propagar | propagar | nunca vira `Feedback` |
| `Offline` | retry manual | retry manual | aguardar ação explícita do usuário |
| `Connection(NOT_SENT)` | retry | retry manual | nada chegou ao servidor |
| `Connection(MAY_HAVE_REACHED_SERVER)` | retry | **terminal** | sem idempotência forte, não repetir |
| `Timeout(CONNECT, NOT_SENT)` | retry | retry manual | |
| `Timeout(RESPONSE, MAY_HAVE...)` | retry | **terminal** | copy conservadora (seção 13.3) |
| `Timeout(OVERALL, *)` | terminal | terminal | deadline estourado, não renovar |
| HTTP 400 | terminal | terminal | erro de contrato |
| HTTP 401 / 403 | terminal | terminal | ação `Authenticate` quando houver login |
| HTTP 404 | terminal | terminal | |
| HTTP 408 | retry | retry manual | |
| HTTP 409 | terminal | terminal | |
| HTTP 422 | terminal | terminal | ação `EditInput` |
| HTTP 429 | retry, respeitando `Retry-After` | retry manual | teto local no `Retry-After` |
| HTTP 500 / 502 / 503 / 504 | retry | **terminal** | efeito pode ter sido aplicado |
| Demais 5xx | terminal | terminal | fail closed |
| `Protocol` | terminal | terminal | repetir não conserta o payload |
| `Unknown` | terminal | terminal | fail closed |

"Retry manual" = o retry automático não acontece, mas a UI oferece o botão. "Terminal" = sem
botão de repetir; a `RecoveryAction` indica o caminho correto.

```kotlin
sealed interface RetryDecision {
    data class RetryAfter(val delay: Duration, val reason: RetryReason) : RetryDecision
    data class Stop(val recovery: RecoveryAction) : RetryDecision
}

enum class RetryReason { BACKOFF, RETRY_AFTER_HEADER, MANUAL }
```

---

## 8. Perfis de operação

Dois perfis cobrem todas as telas existentes. Nenhum deles carrega identidade de operação ou
chave — essa é a diferença central para a seção 7 do plano completo.

```kotlin
enum class OperationKind { READ, COMMAND }

data class OperationSpec(
    val name: OperationName,          // enum estável, não texto livre
    val kind: OperationKind,
    val maxAttempts: Int,             // inclui a tentativa inicial
    val perAttemptTimeout: Duration,
    val overallDeadline: Duration,
    val backoff: BackoffStrategy,
    val concurrency: ConcurrencyPolicy,
)
```

Validações obrigatórias no `init`:

- `maxAttempts >= 1`;
- timeouts positivos e finitos;
- `overallDeadline >= perAttemptTimeout`;
- delay máximo do backoff menor que o deadline total;
- `kind == COMMAND` implica `concurrency != CancelPrevious`.

### 8.1 `Read`

Profile, Orders, Picker items.

- `maxAttempts = 3` (uma inicial + dois retries);
- timeout curto por tentativa, deadline total generoso;
- full jitter;
- `CancelPrevious` permitido — cancelar um GET é seguro;
- falha final oferece retry manual;
- o conteúdo anterior pode ser preservado durante refresh, se a UX quiser.

### 8.2 `Command`

Create Order, Picker send.

- **sem retry automático** para qualquer falha `MAY_HAVE_REACHED_SERVER`;
- retry automático permitido apenas para `NOT_SENT` comprovado;
- `DropWhileRunning` — segundo submit enquanto o primeiro corre é descartado com feedback;
- input capturado no `start(input)`, imutável até o fim da sessão;
- sair da tela cancela a coleta da UI, não deveria cancelar o request em produção (aqui cancela,
  e isso fica documentado como limitação conhecida — resolvê-lo exige a coluna durável).

---

## 9. Máquina de estados

`RetryUiState` já é selado e tem `Idle`; a evolução é acrescentar `BackingOff`, trocar
`canRetry: Boolean` por `RecoveryAction` e corrigir a contagem.

```kotlin
sealed interface RetryUiState<out T> {
    data object Idle : RetryUiState<Nothing>

    data class Loading(
        val attempt: Int,        // 1-based; 1 = carga inicial
        val maxAttempts: Int,
    ) : RetryUiState<Nothing>

    data class BackingOff(
        val nextAttempt: Int,
        val maxAttempts: Int,
        val secondsRemaining: Int,
        val reason: RetryReason,
    ) : RetryUiState<Nothing>

    data class Success<T>(val data: T, val attemptsUsed: Int) : RetryUiState<T>

    data class Feedback(
        val failure: PublicFailure,   // enum estável, sem texto
        val recovery: RecoveryAction,
        val attemptsUsed: Int,
        val maxAttempts: Int,
    ) : RetryUiState<Nothing>
}
```

`Loading.attempt` deixa de ser nullable: hoje `retryAttempt`/`maxRetries` são `null` na primeira
carga e não-nulos no retry, o que força o scaffold a comparar contra `null` para saber se está
estilizando um retry. `attempt == 1` diz a mesma coisa sem nullable.

`BackingOff` é o estado que hoje não existe: o delay do `retryPolicy` roda dentro do `Loading` e o
usuário não sabe que está esperando. Com ele, o countdown fica visível e `secondsRemaining` deixa
de ser um campo opcional dentro de `Loading`.

### 9.1 Ações de recuperação

```kotlin
sealed interface RecoveryAction {
    data object Retry : RecoveryAction
    data object EditInput : RecoveryAction
    data object Authenticate : RecoveryAction
    data object GoBack : RecoveryAction
    data object ContactSupport : RecoveryAction
}
```

O botão e seu label passam a ser consequência da ação, não de um `if (canRetry)` espalhado pela
UI. `ContactSupport` é o destino de uma mutação cujo resultado é desconhecido — é o substituto
honesto e barato do `OutcomeUnknown` + reconciliação do plano completo.

### 9.2 Transições permitidas

| Estado | Evento | Próximo | Observação |
|---|---|---|---|
| Idle | Start | Loading(1) | captura snapshot do input |
| Loading | Success | Success | terminal da sessão |
| Loading | falha retryable | BackingOff | só com decisão positiva |
| Loading | falha terminal | Feedback | não consome novas tentativas |
| Loading | deadline | Feedback | `PublicFailure.Timeout` |
| BackingOff | delay decorrido | Loading(next) | checar deadline antes |
| BackingOff | deadline | Feedback | |
| BackingOff | Start | conforme `ConcurrencyPolicy` | nunca implícito |
| Feedback | Retry manual | Loading | mesma sessão, mesmo input |
| Success | Start | nova sessão | nova intenção |

Transições não listadas são recusadas e registradas no observer.

### 9.3 Contagem correta

- `attempt`: tentativa atual, 1-based.
- `attemptsUsed`: tentativas já iniciadas.
- `maxAttempts`: **inclui** a inicial. `3` = uma carga + dois retries.
- `retriesUsed = attemptsUsed - 1`, apenas como propriedade derivada se a UI precisar.
- Remover `DEFAULT_MAX_RETRIES` (alias de `DEFAULT_MAX_ATTEMPTS` com o mesmo valor, hoje uma
  armadilha de leitura) e renomear o parâmetro `maxRetries` de `RetryControllerFactory.create`
  para `maxAttempts`, que é o que ele de fato alimenta.
- Corrigir testes e documentação que chamam três attempts de três retries.

---

## 10. Backoff, jitter e limites de tempo

### 10.1 Full jitter

```text
cap   = min(maxDelay, baseDelay × factor^retryIndex)     // retryIndex = 0 no primeiro retry
delay = random(0, cap)
```

Regras: cálculo saturado contra overflow; `base`, `factor` e `maxDelay` validados no `init`; delay
nunca negativo nem infinito; `Random` injetável; **delay limitado pelo deadline restante**.

O `ExponentialBackoffPolicy` atual aplica jitter de 80%–100% (`1.0 + random.nextDouble(-0.2, 0.0)`)
e já satura no `max` antes do jitter — a mudança é ampliar a faixa para `random(0, cap)`, que
dispersa clientes de verdade, e passar a receber o deadline restante como limite.

### 10.2 `Retry-After`

- suportar segundos e data HTTP;
- aplicar teto local configurável (não esperar minutos por ordem do servidor);
- valor inválido ou negativo → usar backoff local;
- nunca ultrapassar o deadline restante;
- registrar no observer que o header foi usado, com a duração normalizada.

### 10.3 Timeouts

| Limite | Onde | Papel |
|---|---|---|
| connect / socket / request | `HttpTimeout` do Ktor | protege o socket |
| `perAttemptTimeout` | `RetryExecutor` | garante limite mesmo se o engine não cumprir |
| `overallDeadline` | `RetryExecutor` | teto da sessão inteira, incluindo backoff |

O deadline usa `TimeSource.Monotonic` injetável, para que os testes o controlem com virtual time
sem depender de relógio de parede.

### 10.4 Automático versus manual

- Retry **automático** corrige falha transitória dentro de uma execução lógica.
- Retry **manual** é decisão do usuário após feedback; consome o orçamento da mesma forma.
- A UI distingue `BackingOff` (espera automática) de `Feedback` (espera pelo usuário).
- Para read, um retry manual pode iniciar sessão nova e deadline novo.
- Para command, o retry manual reusa o snapshot do `start`, nunca o formulário vivo.

---

## 11. Concorrência e cancelamento

### 11.1 Serialização

`job` e `generation` são `var` sem proteção. Dois `retry()` de dispatchers diferentes podem ler
`_state.value` antes de qualquer escrita e passar os dois pelo guard.

A publicação síncrona de `Loading` antes do `launch` já fecha a janela do double tap **sequencial
na mesma thread** — que é o caso real da UI Compose — mas não o concorrente. A correção é tornar
o comando uma operação atômica, com um `Mutex` ou um `Channel<Command>` consumido por uma única
coroutine. Para este plano, `Mutex` basta: o número de comandos é pequeno e todos partem do
`viewModelScope`.

Documentar explicitamente o confinamento esperado na KDoc pública do controller.

### 11.2 Políticas

```kotlin
sealed interface ConcurrencyPolicy {
    data object CancelPrevious : ConcurrencyPolicy   // reads
    data object DropWhileRunning : ConcurrencyPolicy // commands
}
```

`JoinExisting`, `Queue` e `Reject` do plano completo ficam de fora: exigem chave de equivalência
ou fila persistente, e nenhuma tela atual precisa deles.

### 11.3 Snapshot imutável

Hoje a segurança depende de convenção: `CreateOrderViewModel` guarda `lastSubmittedRequest` e a
closure passada ao factory lê essa var; `PickerViewModel` faz o mesmo com `itemToSend`. Funciona,
mas nada no tipo impede que alguém passe uma closure lendo `_formInput.value`.

A API passa a associar o input à sessão:

```kotlin
controller.start(input = validatedRequest)
```

e o executor recebe:

```kotlin
suspend fun execute(input: Input, context: AttemptContext): Output
```

Assim uma edição posterior do formulário não tem como alterar uma tentativa já criada — a
garantia vira estrutural em vez de disciplinar.

### 11.4 Cancelamento

- `CancellationException` nunca é classificada nem exibida (comportamento atual, preservado).
- Cancelar um read é sempre seguro.
- Cancelar um command **não** reverte nada no servidor. Sem persistência, a limitação é real e
  deve estar documentada em KDoc e no README, não escondida.
- Resultado de sessão antiga nunca altera a sessão atual (papel do `generation`, mantido).

---

## 12. Rede e Ktor

### 12.1 `HttpTimeout`

`NetworkModule` hoje instala apenas `ContentNegotiation` e `Logging`. Adicionar `HttpTimeout` com
connect, socket e request timeouts. Os valores iniciais são do projeto de estudo e devem estar
nomeados em constantes, com comentário dizendo que produção exige calibração por percentil de
latência real.

### 12.2 Owner único

`RetryExecutor` é o único dono das tentativas. `HttpRequestRetry` não é instalado, e o retry
automático do engine OkHttp é desabilitado (`retryOnConnectionFailure(false)`), para que a
contagem de attempts do executor corresponda ao número real de requests.

### 12.3 Mapeamento de resposta

Evolução do `ApiClient`, que hoje colapsa qualquer `IOException` em `Connection()`:

- distinguir timeout de conexão (`NOT_SENT`) de timeout de resposta (`MAY_HAVE_REACHED_SERVER`);
- capturar status, `Retry-After` e request ID;
- classificar falha de deserialização como `Protocol`, não como conexão;
- limitar o tamanho do corpo de erro lido;
- preservar a cause apenas para diagnóstico interno;
- nunca devolver DTO ou exception do Ktor para domínio/UI.

O `ScenarioHolder` ganha cenários novos para exercitar os casos acima: timeout de conexão, timeout
de resposta, `429` com `Retry-After` e corpo malformado. É o que mantém o app demonstrável sem
depender de o servidor falhar sozinho.

### 12.4 Logging e cleartext

- `LogLevel.ALL` somente em debug; release usa `LogLevel.NONE`.
- Redigir `Authorization`, cookies e `Idempotency-Key` antes de qualquer log.
- Manter `usesCleartextTraffic` restrito ao build de debug, via Network Security Config separada —
  hoje ele está no manifest principal e vale para release também.

---

## 13. Apresentação

### 13.1 Núcleo sem copy

`FeedbackErrorData` (título, descrição, label de botão) e `ErrorType.feedback()` saem de `retry/`.
O núcleo devolve `PublicFailure` + `RecoveryAction`; a UI resolve `@StringRes`.

### 13.2 Estados que a UI deve distinguir

carga inicial · refresh com conteúdo preservado · tentativa técnica em andamento · backoff
automático com countdown · falha retryable · validação que exige editar dados · limite de
tentativas atingido · resultado desconhecido · sucesso.

`RetryStateScaffold` hoje diferencia carga inicial de retry pelo `retryAttempt` nullable; passa a
usar `attempt > 1` e ganha a branch de `BackingOff`.

### 13.3 Regras de comunicação

- Não dizer "falhou" quando o resultado é desconhecido.
- Não oferecer "tente novamente" para uma mutação que pode ter sido concluída. A copy correta é
  do tipo *"Não conseguimos confirmar se o pedido foi criado. Verifique seus pedidos antes de
  enviar de novo."* — com `RecoveryAction.GoBack` ou `ContactSupport`, nunca `Retry`.
- Contagem de attempts é informação técnica; manter visível neste projeto por ser um estudo, com
  comentário explicando que em produção isso é decisão de UX.
- Anunciar mudanças de estado para acessibilidade sem repetir o anúncio a cada recomposição.

---

## 14. Observabilidade mínima

Um observer pequeno, o suficiente para tornar o comportamento auditável em teste e em Logcat, sem
plataforma de métricas:

```kotlin
interface RetryObserver {
    fun onAttemptStarted(operation: OperationName, attempt: Int)
    fun onAttemptFailed(operation: OperationName, attempt: Int, failure: RequestFailure)
    fun onRetryScheduled(operation: OperationName, nextAttempt: Int, delay: Duration, reason: RetryReason)
    fun onFinished(operation: OperationName, result: OperationResult)
}
```

Default no-op, para manter os testes simples. Uma implementação de debug loga em Logcat. Regras de
privacidade que já valem aqui: `OperationName` e códigos de falha vêm de enums; nunca logar URL
completa, `exception.message` ou payload.

---

## 15. Estratégia de testes

Mantendo o que o projeto já estabeleceu: JVM tests apenas, JUnit4 puro, fakes escritos à mão,
`MockEngine` via `mockHttpClient`, `MainDispatcherRule` e virtual time.

### 15.1 Classificador

Teste em tabela cobrindo: `NOT_SENT` versus `MAY_HAVE_REACHED_SERVER` por estágio de timeout;
cada status da matriz da seção 7; `Retry-After` em segundos, data HTTP, inválido, negativo e acima
do teto; erro de serialização → `Protocol`; exception desconhecida → `Unknown` terminal;
`CancellationException` sempre propagada; cause preservada mas ausente do estado público.

### 15.2 Backoff

Primeira tentativa sem backoff; índice do primeiro retry inequívoco; crescimento exponencial;
saturação no teto; ausência de overflow com attempts altos; full jitter dentro da faixa com
`Random` determinístico; delay nunca negativo, infinito ou maior que o deadline restante;
cancelamento durante o delay.

### 15.3 Máquina de estados

Toda transição permitida e as principais proibidas; sucesso na primeira tentativa; sucesso após N;
falha terminal não consome tentativas extras; deadline durante request e durante backoff; retry
manual preserva o input; novo `start` cria sessão nova; resultado obsoleto não afeta a sessão
atual. O teste de cancelamento existente em `RetryControllerTest` é regressão obrigatória — se o
pipeline de Flow do `run()` mudar, ele roda primeiro.

### 15.4 Concorrência

`retry()` simultâneo de dispatchers diferentes; múltiplos `start()` simultâneos; double tap antes
de a coroutine executar e durante o request; start durante backoff; cancelamento e sucesso
chegando juntos; `DropWhileRunning` descartando o segundo submit sem efeito duplicado.

### 15.5 MockEngine

Headers e body preservados entre tentativas (o `CreateOrderViewModelTest` já inspeciona bodies
capturados — estender); número real de requests igual a `attemptsUsed`, provando que não há retry
oculto no engine; status + `Retry-After` capturados; body malformado → `Protocol`; content type
inesperado; resposta lenta disparando `perAttemptTimeout`.

### 15.6 Fora

Sem testes de process death, migração de schema, carga, caos ou contrato de idempotência — as
features correspondentes não existem neste plano.

---

## 16. Plano de implementação por fases

Cada fase é um conjunto pequeno de PRs e só avança quando seus critérios de aceite passam. Não há
Fase 0 de alinhamento externo: as decisões deste escopo cabem no repositório.

### Fase 1 — erros tipados e apresentação desacoplada

**Objetivo:** parar de decidir a partir de `Throwable` e tirar copy do núcleo.

- criar `RequestFailure`, `TimeoutStage` e `OutcomeCertainty`;
- evoluir o `ApiClient` para produzir `RequestFailure` com certeza correta por estágio;
- criar `FailureClassifier` e `RetryDecider` com a matriz da seção 7;
- criar `RecoveryAction` e `PublicFailure`;
- mover os textos de `ErrorTypeStrategy` para `strings.xml` e um mapper de presentation;
- tornar `Unknown` e `Protocol` terminais;
- manter `RetryUiState.Feedback` funcionando via adapter temporário, marcado para remoção.

**Aceite:** nenhum texto de UI em `retry/`; nenhum 4xx retryable por default; cancelamento não
vira falha; matriz inteira coberta por teste em tabela.

### Fase 2 — `RetryExecutor` com limites

**Objetivo:** isolar a execução das tentativas em um componente puro e limitado.

- criar `OperationSpec`, `OperationName` e os dois perfis;
- criar `AttemptContext` e a assinatura `execute(input, context)`;
- implementar timeout por tentativa e deadline global com `TimeSource` injetável;
- trocar o jitter atual por full jitter, limitado pelo deadline restante;
- integrar `Retry-After` com teto local;
- criar `RetryDecision` selada e o observer no-op;
- validar toda a configuração no `init`.

**Aceite:** executor não importa Android, Compose nem Hilt; nenhuma tentativa ocorre depois do
deadline; numeração de attempts inequívoca; cancelamento transparente durante request e backoff.

### Fase 3 — controller serializado com snapshot

**Objetivo:** eliminar corridas e representar o backoff como estado.

- adicionar `BackingOff` ao `RetryUiState` e `Loading.attempt` não-nullable;
- trocar `canRetry: Boolean` por `RecoveryAction`;
- serializar comandos com `Mutex`;
- mudar a API para `start(input)` com snapshot imutável;
- implementar `CancelPrevious` e `DropWhileRunning`;
- padronizar `attempts`/`retries`, removendo `DEFAULT_MAX_RETRIES` e renomeando `maxRetries`;
- unificar `create` e `createWithErrorStrategy` em um método só;
- documentar ownership e confinamento na KDoc.

**Aceite:** testes de concorrência passam repetidamente; toda transição é determinística;
resultado obsoleto nunca afeta a sessão atual; command nunca usa `CancelPrevious`.

### Fase 4 — borda HTTP

**Objetivo:** tornar a borda de rede explícita e segura.

- instalar `HttpTimeout` com valores nomeados;
- desabilitar retry do engine OkHttp e confirmar por teste de contagem de requests;
- mapear timeout por estágio no `ApiClient`;
- restringir `Logging` a debug e redigir headers sensíveis;
- separar Network Security Config de debug e release, bloqueando cleartext em release;
- adicionar os cenários novos ao `ScenarioHolder` (timeout de conexão, timeout de resposta, 429
  com `Retry-After`, corpo malformado);
- cobrir tudo com `MockEngine`.

**Aceite:** request lento termina dentro do limite; build release não registra bodies/headers;
nenhuma tentativa escondida altera a contagem; cleartext bloqueado fora de debug.

### Fase 5 — migração das telas e remoção do legado

**Objetivo:** todos os consumidores na nova API, sem política duplicada.

Ordem: Profile → Orders → Picker items → Picker send → Create Order. Reads antes de mutações.

- migrar cada ViewModel para `start(input)`, mantendo um `XxxUiState` por tela;
- adaptar `RetryStateScaffold` a `BackingOff` e `RecoveryAction`;
- aplicar `DropWhileRunning` a Picker send e Create Order;
- aplicar a copy conservadora (13.3) às mutações;
- remover os adapters temporários e o `ErrorTypeStrategy` antigo;
- atualizar `CLAUDE.md`/`AGENTS.md` e o README.

**Aceite:** paridade funcional; sem regressão de navegação ou effects; nenhum consumidor do
contrato antigo; código legado removido; documentação atualizada.

---

## 17. Sequência sugerida de PRs

1. `refactor(error): introduce typed request failures`
2. `feat(error): classify outcome certainty per timeout stage`
3. `refactor(retry): separate retry decisions from presentation copy`
4. `feat(retry): add operation specs with timeouts and deadlines`
5. `feat(retry): add pure retry executor with full jitter and retry-after`
6. `feat(retry): expose backing-off state and recovery actions`
7. `refactor(retry): serialize controller commands and capture input snapshots`
8. `refactor(retry): standardize attempt counting vocabulary`
9. `fix(network): configure explicit timeouts and single retry owner`
10. `fix(network): restrict logging and cleartext to debug builds`
11. `feat(demo): add timeout and rate-limit scenarios`
12. `refactor(profile): migrate profile to the new controller API`
13. `refactor(orders): migrate order reads to the new controller API`
14. `refactor(picker): migrate picker reads and send`
15. `refactor(createorder): migrate create order with drop-while-running`
16. `refactor(retry): remove legacy error strategy and adapters`

Cada PR deve ser pequeno o bastante para revisão completa e não misturar mudança estrutural com
migração de todas as telas de uma vez.

---

## 18. Critérios de pronto

- [ ] falhas são tipadas e a regra default é fail closed;
- [ ] nenhum 4xx é retryable sem decisão explícita e teste;
- [ ] `OutcomeCertainty` impede retry automático de mutação possivelmente aplicada;
- [ ] timeout por tentativa e deadline global configurados e testados;
- [ ] `Retry-After` tratado com teto local;
- [ ] backoff usa full jitter, limitado pelo deadline restante;
- [ ] retry tem owner único e a contagem de attempts bate com o número real de requests;
- [ ] input da sessão é imutável entre tentativas, garantido pelo tipo;
- [ ] double tap e comandos concorrentes não geram efeito duplicado;
- [ ] `BackingOff` é visível na UI;
- [ ] `RecoveryAction` substituiu `canRetry` em todos os consumidores;
- [ ] nenhum texto de UI em `retry/`, tudo em `strings.xml`;
- [ ] logs de release não contêm headers ou bodies; cleartext restrito a debug;
- [ ] vocabulário `attempts`/`retries` padronizado em código, testes e docs;
- [ ] testes de classificador, backoff, máquina de estados, concorrência e `MockEngine` passando;
- [ ] `CLAUDE.md` e README refletem a nova arquitetura e suas limitações conhecidas.

---

## 19. Riscos e mitigação

| Risco | Impacto | Mitigação |
|---|---|---|
| Sem persistência, um command cancelado pela saída da tela fica sem resultado conhecido | usuário sem confirmação | copy conservadora + limitação documentada; resolver exige o plano completo |
| Sem idempotência forte, mutação nunca é repetida automaticamente | mais falhas visíveis em rede ruim | decisão consciente: falso negativo é preferível a efeito duplicado |
| Retry duplicado entre executor e engine | carga e contagem errada | owner único + teste contando requests no `MockEngine` |
| `RequestFailure` crescer e virar uma segunda `ErrorTypeStrategy` | acoplamento de volta | limitar o tipo à seção 6.1; copy só na presentation |
| Migração parcial deixando duas APIs vivas | confusão e bug | adapters marcados para remoção, com a Fase 5 obrigando a limpeza |
| Timeouts arbitrários mal calibrados | falso negativo em rede lenta | constantes nomeadas e comentadas como valores de estudo |
| Complexidade demais para reads simples | manutenção pior | dois perfis apenas; `RetryController<Unit, T>` mantém read trivial |

---

## 20. O que fica para o plano completo

Registrado aqui para que a ausência seja uma escolha visível, e não um esquecimento. Ver
[plano-evolucao-retry-contexto-bancario.md](plano-evolucao-retry-contexto-bancario.md) para o
detalhamento de cada item.

| Item | Seção no plano completo | Pré-requisito |
|---|---|---|
| Idempotency key tipada, fingerprint e conflito de payload | 11 | contrato de backend |
| Persistência de comandos e retomada após process death | 12 | Room + política de dados |
| `OutcomeUnknown` com reconciliação | 5.4, 11.3 | endpoint de status |
| WorkManager e continuidade fora da UI | 12.4 | persistência |
| Circuit breaker, bulkhead, retry budget | 16 | tráfego real e métricas |
| Auth refresh single-flight | 6.4 | autenticação no app |
| Métricas, tracing e dashboards | 15 | plataforma escolhida |
| Certificate pinning e política de TLS | 13.4 | time de segurança |
| Feature flags, SLOs, rollout e runbooks | 18, 20, 23 | times externos |
| Testes de carga, caos e process death | 17.7, 17.8 | infraestrutura de teste |

Nenhuma decisão deste plano fecha a porta para esses itens: `RequestFailure` já carrega
`OutcomeCertainty` (a base do `OutcomeUnknown`), `OperationSpec` é o ponto onde identidade e
persistência entram depois, e o `RetryObserver` é a costura pronta para métricas reais.
