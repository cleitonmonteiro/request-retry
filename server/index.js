const http = require('http');

const PORT = process.env.PORT || 8090;

const profile = { full_name: 'Ada Lovelace', email_address: 'ada@example.com' };

const orders = [
  { order_id: 'A-1001', item_name: 'Mechanical keyboard', total_amount: '89.90' },
  { order_id: 'A-1002', item_name: 'USB-C hub', total_amount: '34.50' },
  { order_id: 'A-1003', item_name: 'Monitor arm', total_amount: '129.00' },
];

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

function sendJson(res, status, body) {
  const json = JSON.stringify(body);
  res.writeHead(status, { 'Content-Type': 'application/json' });
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
    readJsonBody(req)
      .then((body) => {
        const created = {
          order_id: `A-${1000 + orders.length + 1}`,
          item_name: body.item_name || 'Untitled item',
          total_amount: ((Number(body.quantity) || 1) * 19.99).toFixed(2),
        };
        orders.push(created);
        sendJson(res, 201, created);
      })
      .catch(() => sendJson(res, 400, { error: 'Invalid JSON body' }));
    return; // response is written asynchronously once the body resolves
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

server.listen(PORT, () => {
  console.log(`RequestRetry mock server listening on http://localhost:${PORT}`);
});
