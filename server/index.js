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

function sendJson(res, status, body) {
  const json = JSON.stringify(body);
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(json);
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
  if (req.method === 'GET' && url.pathname === '/items') {
    return sendJson(res, 200, items);
  }
  const sendMatch = req.method === 'POST' && url.pathname.match(/^\/items\/([^/]+)\/send$/);
  if (sendMatch) {
    return sendJson(res, 200, { item_id: sendMatch[1] });
  }

  sendJson(res, 404, { error: `No mocked route for ${req.method} ${url.pathname}` });
});

server.listen(PORT, () => {
  console.log(`RequestRetry mock server listening on http://localhost:${PORT}`);
});
