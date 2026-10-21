/* 原神牛逼 · 局域网联机小助手
   用法（在本文件所在目录）：
     node duoren-server.js            默认端口 2648
     node duoren-server.js 2648       指定端口
   启动后把面板上打印的 http://<你的内网IP>:2648 发给同一 WiFi/内网的朋友，
   他们打开这个地址、填个名字就能进游戏，并且自动互相发现（不用贴连接码）。
   你本机双击 原神启动.html（file:// 打开）则是不联机的纯单机。

   只用到 Node 自带的模块，不用 npm install 任何东西。
   这台"服务器"只做两件事：把 html 发出去 + 在大厅里转发消息。
   它只认识浏览器随机生成的一个 id，不记录昵称、存档、位置之外的任何东西，
   所有个人信息仍然只存在各自浏览器的 localStorage / sessionStorage 里。 */
'use strict';
const http = require('http');
const fs = require('fs');
const path = require('path');
const os = require('os');

const DIR = __dirname;
const HOME = path.join(DIR, '原神启动.html');
const PORT = +(process.argv[2] || 2648);
const GAME = '原神启动.html';
const AUTO_OPEN = process.argv.indexOf('--open') >= 0;   // duoren.bat 用它顺手打开本机页面

/* 大厅里所有长连接：id -> {res, since} */
const lobby = new Map();
let relayed = 0;

function lanAddrs() {
  const out = [];
  const ifs = os.networkInterfaces();
  for (const name of Object.keys(ifs)) {
    for (const a of ifs[name] || []) {
      if (a.family === 'IPv4' && !a.internal) out.push(a.address);
    }
  }
  return out;
}

function sseHead(res) {
  res.writeHead(200, {
    'Content-Type': 'text/event-stream; charset=utf-8',
    'Cache-Control': 'no-cache, no-transform',
    'Connection': 'keep-alive',
    'X-Accel-Buffering': 'no'
  });
  res.write('retry: 2000\n\n');
}

function broadcast(from, payload) {
  const line = 'data: ' + payload + '\n\n';
  for (const [id, c] of lobby) {
    if (id === from) continue;                // 自己发的不回给自己
    try { c.res.write(line); } catch (e) { lobby.delete(id); }
  }
  relayed++;
}

function readBody(req, cb) {
  let size = 0, chunks = [];
  req.on('data', function (b) {
    size += b.length;
    if (size > 65536) { req.destroy(); return; }   // 单条消息上限 64KB，防呆
    chunks.push(b);
  });
  req.on('end', function () { cb(Buffer.concat(chunks).toString('utf8')); });
  req.on('error', function () { });
}

/* 默认只发游戏本体，别把这台机器上别的在写的 html 泄露到局域网里。
   （调试时要发别的页面，加 --all-html 启动。） */
const ALL_HTML = process.argv.indexOf('--all-html') >= 0;
function safeHtml(url) {
  const name = decodeURIComponent(url).replace(/^\/+/, '');
  if (!/^[\w\u4e00-\u9fa5 \-]+\.html$/.test(name)) return null;
  if (!ALL_HTML && name !== GAME) return null;
  const p = path.join(DIR, name);
  if (path.dirname(p) !== DIR) return null;
  return fs.existsSync(p) ? p : null;
}

const server = http.createServer(function (req, res) {
  const u = (req.url || '/').split('?')[0];
  const q = new URLSearchParams((req.url || '').split('?')[1] || '');

  if (u === '/lobby') {
    const id = String(q.get('id') || Math.random().toString(36).slice(2));
    if (lobby.has(id)) { try { lobby.get(id).res.end(); } catch (e) { } }
    sseHead(res);
    lobby.set(id, { res: res, since: Date.now() });
    res.write('data: "hello"\n\n');
    req.on('close', function () { lobby.delete(id); });
    return;
  }

  if (u === '/say' && req.method === 'POST') {
    const id = String(q.get('id') || '');
    readBody(req, function (body) {
      body = body.replace(/[\r\n]+/g, '').slice(0, 65536);
      if (body && body[0] === '{') broadcast(id, body);
      res.writeHead(204, { 'Cache-Control': 'no-store' });
      res.end();
    });
    return;
  }

  if (u === '/peers') {
    const list = [];
    for (const [id, c] of lobby) list.push(id + ' ' + Math.round((Date.now() - c.since) / 1000) + 's');
    res.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('大厅里 ' + lobby.size + ' 人 · 已转发 ' + relayed + ' 条\n' + list.join('\n') + '\n');
    return;
  }

  let file = null;
  if (u === '/' || u === '/index.html' || u === '/' + GAME) file = HOME;
  else file = safeHtml(u);
  if (!file) {
    res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('404\n');
    return;
  }
  let buf;
  try { buf = fs.readFileSync(file); }
  catch (e) {
    res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('读不到 ' + path.basename(file) + '：' + e.message + '\n');
    return;
  }
  res.writeHead(200, {
    'Content-Type': 'text/html; charset=utf-8',
    'Cache-Control': 'no-store',
    'Content-Length': buf.length
  });
  res.end(buf);
});

server.on('error', function (e) {
  if (e.code === 'EADDRINUSE') {
    console.log('端口 ' + PORT + ' 已经被占用：可能已经开着一个了。');
    console.log('想重开请先关掉原来的窗口，或者换端口：node duoren-server.js ' + (PORT + 1));
  } else console.log('启动失败：' + e.message);
  setTimeout(function () { process.exit(1); }, 1200);
});

server.listen(PORT, '0.0.0.0', function () {
  const ips = lanAddrs();
  console.log('===============================================');
  console.log(' 原神牛逼 · 局域网大厅已启动，端口 ' + PORT);
  console.log('===============================================');
  console.log(' 你自己进入：      http://127.0.0.1:' + PORT + '/');
  if (ips.length) {
    console.log(' 发给朋友的地址：');
    for (const ip of ips) console.log('      http://' + ip + ':' + PORT + '/');
  } else {
    console.log(' 没找到内网 IP：这个网页可能只能本机能开。');
  }
  console.log(' 朋友打不开时的排查：');
  console.log('   1) Windows 防火墙弹窗要点「允许」（或临时关闭专用网络防火墙）');
  console.log('   2) 必须在同一个 WiFi / 同一路由器下，跨网段、访客网络通常不通');
  console.log('   3) 路由器开了「AP 隔离 / 客户端隔离」的话局域网也互相不通');
  console.log(' 这个窗口保持开着就是服务器；关掉窗口 = 大家退出大厅。');
  console.log(' 大厅里现在的人数：可在 http://127.0.0.1:' + PORT + '/peers 查看');
  console.log('-----------------------------------------------');
  if (AUTO_OPEN) {
    /* 服务已经在听了再开浏览器，晚半秒是为了让首屏别撞上空端口 */
    setTimeout(function () {
      try {
        require('child_process').exec('cmd /c start "" "http://127.0.0.1:' + PORT + '/"');
      } catch (e) { }
    }, 400);
  }
});

/* 长连接没有数据时也要定期探一下活，顺便清掉已经断掉的客户端 */
setInterval(function () {
  for (const [id, c] of lobby) {
    try { c.res.write(':ping\n\n'); }
    catch (e) { lobby.delete(id); }
  }
}, 20000).unref();
