# 3A-Games

> **AI 写的多人娱乐游戏大杂烩。**
> 这里没有产品经理，没有排期，没有“下个版本一定修”，只有灵感、代码，以及一群不知道为什么能跑起来的游戏。

本仓库收录了几个 3A 级（**A**I、**A**pparently、**A**mazing）项目。画面、玩法、网络架构和 bug 数量都在努力突破人类想象力的上限。

## 项目列表

| 目录 | 名称 | 技术栈 | 启动方式 |
|---|---|---|---|
| [`aedfh/`](./aedfh) | AEDFH 3D 世界 / Boss 战 | Java 21 + JMonkeyEngine 3.9 + Gradle | 见下方 |
| [`gta/`](./gta) | GTA V WEB：洛圣都浏览器体验 | HTML + CSS + JavaScript | 直接打开 [`gta.html`](./gta/gta.html) |
| [`peace-elite/`](./peace-elite) | 精英战场：网页大逃杀 | 单文件 HTML + Three.js | 直接打开 [`elite-battle-royale.html`](./peace-elite/elite-battle-royale.html) |
| [`ys/`](./ys) | 原神启动：局域网多人娱乐版 | HTML + JavaScript + Node.js | 见下方 |

## 快速开始

### 浏览器游戏

无需构建，无需安装依赖，双击对应的 HTML 文件即可：

```text
gta/gta.html
peace-elite/elite-battle-royale.html
ys/原神启动.html
```

现代浏览器即可。Chrome、Edge、Firefox 随便挑一个，能打开网页就已经具备成为传奇玩家的全部条件。

### 原神局域网多人

想和同一 Wi-Fi 下的朋友互相伤害：

1. 安装 [Node.js](https://nodejs.org/)。
2. 双击 [`ys/duoren.bat`](./ys/duoren.bat)，或在 `ys` 目录执行：

   ```bash
   node duoren-server.js 2648 --open
   ```

3. 把终端显示的局域网地址发给朋友。
4. 保持服务器窗口打开；关掉它，整个多人宇宙就会优雅地坍缩。

默认端口是 `2648`，也可以把端口号作为第一个参数传入。

### AEDFH 3D 项目

这是本仓库里最像“正经游戏”的部分，需要：

- JDK 21
- Gradle（项目脚本期望使用项目内 `.tools/gradle-*`）
- 项目内 `.jdk/jdk-21*` 下的 JDK 21

准备好环境后，在 `aedfh` 目录运行：

```bat
run.bat
```

构建发行版：

```bat
build.bat
```

如果你只是想看看代码，也完全可以跳过环境配置——阅读 Java 源码同样能获得媲美通关的成就感。

## 这是什么级别的项目？

- **多人**：至少支持多人同时产生混乱。
- **AI 速成**：能跑起来就是胜利，能让朋友一起跑起来就是网络奇迹。
- **娱乐**：严肃的人请先深呼吸，本仓库不对任何人的显卡、睡眠和游戏时长负责。
- **开源**：欢迎提 Issue、提 PR，或者提出更狂妄的玩法。

## 贡献

发现 bug？那是隐藏玩法。
发现更多 bug？恭喜，你已经成为核心开发者。

提交代码前请确保：

1. 游戏至少能启动；
2. 不要提交密钥、个人文件或体积离谱的构建产物；
3. 提交信息可以狂妄，但改动最好也确实有用。

## License

各子项目的授权信息以仓库中的 [`LICENSE`](./LICENSE) 为准。
