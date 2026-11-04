import { createReadStream } from 'node:fs'
import { stat } from 'node:fs/promises'
import { createServer } from 'node:http'
import { extname, resolve, sep } from 'node:path'

const root = resolve('/app/dist')
const types = new Map([
  ['.css', 'text/css; charset=utf-8'],
  ['.html', 'text/html; charset=utf-8'],
  ['.ico', 'image/x-icon'],
  ['.js', 'text/javascript; charset=utf-8'],
  ['.json', 'application/json; charset=utf-8'],
  ['.png', 'image/png'],
  ['.svg', 'image/svg+xml'],
])

function send(req, res, status, body, contentType = 'text/plain; charset=utf-8') {
  res.writeHead(status, {
    'Content-Type': contentType,
    'Content-Length': Buffer.byteLength(body),
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
  })
  res.end(req.method === 'HEAD' ? undefined : body)
}

createServer(async (req, res) => {
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    send(req, res, 405, 'Method not allowed')
    return
  }

  let pathname
  try {
    pathname = decodeURIComponent(new URL(req.url, 'http://localhost').pathname)
  } catch {
    send(req, res, 400, 'Bad request')
    return
  }
  if (pathname === '/health/frontend') {
    send(req, res, 200, '{"status":"UP","component":"frontend"}', 'application/json; charset=utf-8')
    return
  }
  if (pathname.startsWith('/api/')) {
    send(req, res, 404, 'Not found')
    return
  }

  let file = resolve(root, `.${pathname}`)
  if (file !== root && !file.startsWith(`${root}${sep}`)) {
    send(req, res, 404, 'Not found')
    return
  }
  if (pathname === '/' || (!extname(pathname) && !pathname.startsWith('/assets/'))) {
    file = resolve(root, 'index.html')
  }

  try {
    const details = await stat(file)
    if (!details.isFile()) {
      send(req, res, 404, 'Not found')
      return
    }
    res.writeHead(200, {
      'Content-Type': types.get(extname(file)) ?? 'application/octet-stream',
      'Content-Length': details.size,
      'Cache-Control': pathname.startsWith('/assets/') ? 'public, max-age=31536000, immutable' : 'no-store',
      'X-Content-Type-Options': 'nosniff',
      'Content-Security-Policy': "default-src 'self'; base-uri 'none'; object-src 'none'; frame-ancestors 'none'",
    })
    if (req.method === 'HEAD') {
      res.end()
      return
    }
    const stream = createReadStream(file)
    stream.on('error', () => res.destroy())
    stream.pipe(res)
  } catch {
    send(req, res, 404, 'Not found')
  }
}).listen(Number(process.env.PORT ?? 8080), '0.0.0.0')
