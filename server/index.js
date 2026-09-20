const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PORT = process.env.PORT || 8090;

const profile = { full_name: 'Ada Lovelace', email_address: 'ada@example.com' };

const orders = [
  { order_id: 'A-1001', item_name: 'Mechanical keyboard', total_amount: '89.90' },
  { order_id: 'A-1002', item_name: 'USB-C hub', total_amount: '34.50' },
  { order_id: 'A-1003', item_name: 'Monitor arm', total_amount: '129.00' },
];
const OPERATIONS_FILE = process.env.OPERATIONS_FILE || path.join(__dirname, '.operations.json');
const operationsById = new Map();
const operationIdByIdempotencyKey = new Map();

function loadOperations() {
  if (!fs.existsSync(OPERATIONS_FILE)) return;
  const stored = JSON.parse(fs.readFileSync(OPERATIONS_FILE, 'utf8'));
  stored.forEach((operation) => {
    operationsById.set(operation.operation_id, operation);
    operationIdByIdempotencyKey.set(operation.idempotency_key, operation.operation_id);
    if (operation.status === 'SUCCEEDED' && operation.order && !orders.some((order) => order.order_id === operation.order.order_id)) {
      orders.push(operation.order);
    }
  });
}

function persistOperations() {
  const temporary = `${OPERATIONS_FILE}.tmp`;
  fs.writeFileSync(temporary, JSON.stringify([...operationsById.values()], null, 2));
  fs.renameSync(temporary, OPERATIONS_FILE);
}

function fingerprint(body) {
  const canonical = JSON.stringify({
    item_name: body.item_name || '',
    quantity: Number(body.quantity) || 0,
    customer_name: body.customer_name || '',
  });
  return crypto.createHash('sha256').update(canonical).digest('hex');
}

const items = [
  { item_id: 'I-1', item_name: 'Backpack' },
  { item_id: 'I-2', item_name: 'Water bottle' },
  { item_id: 'I-3', item_name: 'Notebook' },
];

// One send-response action per item, so the demo exercises all three SDUI action types.
// `label` is the button text — the client renders it as-is, it never hardcodes copy per type.
const sendActionByItemId = {
  'I-1': { action_type: 'deeplink', target: 'requestretry://orders', label: 'View orders' },
  'I-2': { action_type: 'external_link', target: 'https://example.com/track/I-2', label: 'Track shipment' },
  'I-3': { action_type: 'close', label: 'Done' },
};

function sendJson(res, status, body, requestId = crypto.randomUUID()) {
  const json = JSON.stringify(body);
  const headers = { 'Content-Type': 'application/json', 'X-Request-ID': requestId };
  if (body && body.error_code) headers['X-Error-Code'] = body.error_code;
  res.writeHead(status, headers);
  res.end(json);
}

function readJsonBody(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.on('data', (chunk) => { data += chunk; });
    req.on('end', () => {
      try { resolve(data ? JSON.parse(data) : {}); } catch (err) { reject(err); }
    });
    req.on('error', reject);
  });
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`);
  console.log(`${req.method} ${url.pathname}`);

  if (req.method === 'GET' && url.pathname === '/profile') {
    return sendJson(res, 200, profile);
  }
  if (req.method === 'GET' && url.pathname === '/orders') {
    return sendJson(res, 200, orders);
  }
  if (req.method === 'POST' && url.pathname === '/orders') {
    const idempotencyKey = req.headers['idempotency-key'];
    const operationId = req.headers['x-operation-id'];
    if (!idempotencyKey || !operationId) {
      return sendJson(res, 400, { error_code: 'MISSING_OPERATION_IDENTITY' });
    }
    readJsonBody(req)
      .then((body) => {
        const payloadFingerprint = fingerprint(body);
        const existingOperationId = operationIdByIdempotencyKey.get(idempotencyKey);
        const existing = existingOperationId ? operationsById.get(existingOperationId) : operationsById.get(operationId);
        if (existing) {
          if (
            existing.operation_id !== operationId ||
            existing.idempotency_key !== idempotencyKey ||
            existing.payload_fingerprint !== payloadFingerprint
          ) {
            return sendJson(res, 409, { error_code: 'IDEMPOTENCY_CONFLICT' });
          }
          if (existing.status === 'PROCESSING') {
            return sendJson(res, 202, { operation_id: operationId, status: 'PROCESSING' });
          }
          if (existing.status === 'SUCCEEDED') return sendJson(res, 201, existing.order);
          return sendJson(res, 422, { error_code: existing.error_code || 'ORDER_REJECTED' });
        }

        const operation = {
          operation_id: operationId,
          idempotency_key: idempotencyKey,
          payload_fingerprint: payloadFingerprint,
          status: 'PROCESSING',
          order: null,
          error_code: null,
        };
        operationsById.set(operationId, operation);
        operationIdByIdempotencyKey.set(idempotencyKey, operationId);
        persistOperations();

        const created = {
          order_id: `A-${1000 + orders.length + 1}`,
          item_name: body.item_name || 'Untitled item',
          total_amount: ((Number(body.quantity) || 1) * 19.99).toFixed(2),
        };
        orders.push(created);
        operation.status = 'SUCCEEDED';
        operation.order = created;
        persistOperations();
        sendJson(res, 201, created);
      })
      .catch(() => sendJson(res, 400, { error_code: 'INVALID_JSON' }));
    return; // response is written asynchronously once the body resolves
  }
  const operationMatch = req.method === 'GET' && url.pathname.match(/^\/operations\/([^/]+)$/);
  if (operationMatch) {
    const operationId = decodeURIComponent(operationMatch[1]);
    const operation = operationsById.get(operationId);
    if (!operation) {
      return sendJson(res, 200, { operation_id: operationId, status: 'UNKNOWN' });
    }
    return sendJson(res, 200, {
      operation_id: operation.operation_id,
      status: operation.status,
      order: operation.order,
      error_code: operation.error_code,
    });
  }
  if (req.method === 'GET' && url.pathname === '/items') {
    return sendJson(res, 200, items);
  }
  const sendMatch = req.method === 'POST' && url.pathname.match(/^\/items\/([^/]+)\/send$/);
  if (sendMatch) {
    const itemId = sendMatch[1];
    const action = sendActionByItemId[itemId] || { action_type: 'close', label: 'Done' };
    return sendJson(res, 200, { item_id: itemId, action });
  }

  sendJson(res, 404, { error: `No mocked route for ${req.method} ${url.pathname}` });
});

loadOperations();

server.listen(PORT, () => {
  console.log(`RequestRetry mock server listening on http://localhost:${PORT}`);
});
