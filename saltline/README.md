# Saltline — 网页 3D FPS 源码整理包

从 Claude 分享链接 `https://claude.ai/artifact/BGY1SztrAYcJQHSsnHHGmp` 还原出的完整可运行源码。
整理日期：2026-11-11

---

## 1. 这是什么

一个跑在浏览器里的第一人称射击游戏（Three.js r180 + Web Audio）。设定是港口安全网络失控，玩家要切断三处上行中继、然后跑到码头的小船撤离。

- 模式：Mission（任务）/ Holdout（死守）
- 难度：Standard / Hard / Brutal
- 武器：PAC-9 Longshore（主武器）、PAC-4 Bosun（副武器）
- 视角：键鼠、手柄、触屏三套控制
- 素材：全部程序化生成（几何体、贴图、音效都是运行时算出来的），不下载任何模型/音频文件
- 外部依赖：只有 Google Fonts 的字体样式表；断网时会退化成系统字体，游戏照常运行

---

## 2. 目录结构

```
saltline/
├─ index.html              单文件可玩版（1.65 MB，双击即玩，推荐）
├─ 启动游戏.bat            有 Python 就起本地服务，没有就直接用 file:// 打开
├─ README.md               本说明
├─ src/                    拆分后的工程版
│  ├─ index.html           只有 DOM 结构（<1 KB）
│  ├─ game.css             界面样式，34 KB，带分段注释
│  ├─ game.js              原始打包代码，1.62 MB（three.js + 游戏逻辑，压缩过）
│  └─ game.pretty.js       同一份代码格式化后，2.29 MB / 60,854 行，可读可改
└─ _raw/
   ├─ frame-response.html  从 claudeusercontent 抓到的原始响应（含官方旧版兜底副本）
   └─ legacy-v8.html       解析出的上一个版本（带 “Back to the updated version” 横条）
```

两者等价：`index.html` 是内联版，`src/` 是拆开版，改代码建议在 `src/` 里动，最后再合并回单文件。

---

## 3. 怎么运行

- 最简单：双击 `index.html`，浏览器直接打开（Chrome / Edge 都行）。
- 推荐：双击 `启动游戏.bat`，本地起 `http://127.0.0.1:8599`（某些浏览器对 `file://` 的指针锁定更严格，走 http 更稳）。
- 首次进入会有一段约 6–8 秒的构建过程（日志里能看到 render / level / ground / weapon / enemies 各阶段耗时），这是在实时生成场景和武器模型。

### 操作

| 动作 | 键鼠 | 手柄 | 触屏 |
|---|---|---|---|
| 移动 | WASD | 左摇杆 | 左半屏拖动 |
| 视角 | 鼠标 | 右摇杆 | 右半屏拖动 |
| 射击 | 左键 | RT | FIRE 按钮 |
| 瞄准 | 右键 | LT | AIM 按钮 |
| 换弹 | R | X | RELOAD |
| 切副武器 | Q | Y | SIDEARM |
| 切断中继 | E | X | 交互提示 |
| 跳跃 / 冲刺 / 蹲 | Space / Shift / C | — | 屏幕按钮 |
| 暂停 | Esc | — | — |

---

## 4. 代码结构（看 `game.pretty.js`）

- 第 1 ~ 约 50,400 行：Three.js r180 内核（压缩后整包内联，所以体积大）。
- 约 50,400 行往后：真正的游戏代码，大致按这几块组织：
  - 启动与资源构建（`[boot]` 系列日志）
  - 关卡：集装箱、龙门吊、码头、地面、灯光与雾
  - 武器：PAC-9 / PAC-4 的模型、动画、后坐力
  - 敌人 AI 与三处中继（relay）交互
  - HUD、菜单、字幕 / 无线电台词（radio:line）
  - 输入层：键鼠 + 指针锁定、手柄、触屏
  - 音频：Web Audio 实时合成
  - 渲染质量自适应（帧时超过阈值会自动降档，日志里能看到 `dropping quality`）

搜索这些关键词比较好定位：`[boot]`、`[weapon]`、`[render]`、`relay`、`radio:line`、`PAC-9`、`pointerlock`。

---

## 5. 已验证

- 线上版与本地版画面一致（标题界面逐像素对比无明显差异）。
- 本地单文件版与 `src/` 拆分版都能完整启动：控制台依次输出 boot / weapon / enemies 日志，画布正常渲染。
- 唯一没法在自动化里验证的是“点击 DEPLOY 后锁定鼠标”（浏览器自动化环境禁用 pointer lock），人工打开完全正常。

---

## 6. 提取方式（留档）

1. `https://claude.ai/artifact/<id>` 由 Cloudflare 保护，直接请求返回 403。
2. 页面把 artifact 放在 `*.frame.claudeusercontent.com` 的 iframe 里，该 frame 地址不受同样的拦截，直接 curl 就能拿到完整文档（3.59 MB）。
3. 该文档 = 框架运行时 + 线上正片（title / 字体 / `<style>` / `#app` 结构）+ 内嵌的旧版 base64 兜底副本 + 游戏主包 `<script>`。
4. 去掉框架运行时和旧版副本，补上 `<!doctype html>`、`<head>`、`</body></html>`，即得到 `index.html`。
5. `_raw/frame-response.html` 是原始响应，`_raw/legacy-v8.html` 是从中解出的旧版本，可作对照。

---

## 7. 想改点什么

- 改界面文字 / 标题：`src/game.js` 里搜 `SALTLINE`、`The port`、`DEPLOY`（注意压缩代码里的字符串是 `\u2019` 形式的转义）。
- 改配色：`src/game.css` 顶部 `:root` 的 `--signal`（主绿色）、`--bone`（文字色）。
- 改难度/数值：搜 `Brutal`、`Standard`、`health`、`maxHealth`。
- 改完 `src/` 里的文件，把 `game.js` 和 `game.css` 重新内联回 `index.html` 就得到单文件版。

---

版权归原作者所有，本包仅作本地研究与学习使用。