# Legado with MD3

<p align="left">
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white">
  <img alt="License GPL-3.0" src="https://img.shields.io/badge/License-GPL--3.0-blue">
</p>

## 📖 项目简介

**Legado with MD3** 是基于开源阅读器 [阅读（Legado）](https://github.com/gedoor/legado) 的第三方非官方分支，
当前在 [HapeLee/legado-with-MD3](https://github.com/HapeLee/legado-with-MD3) 的 Material Design 3 版本基础上
继续维护。

项目以 Material Design 3 重构界面，保留 Legado 的书源、订阅与本地阅读能力，目标是在不牺牲规则兼容性和
阅读性能的前提下，提供更现代、流畅且一致的阅读体验。

> [!NOTE]
> - 主题系统基于分支自有的 Monet 引擎重构，官方版本的主题不能直接沿用。
> - 最低支持 Android 8.0（minSdk 26）；动态取色与自定义主题由分支主题系统提供。

---

## ✨ 分支特性

相比于官方版本，本项目具有以下独有特性：

* **Material 3 主题：** 动态取色、自定义主题、**预测性返回手势** 与 **共享元素动画**。
* **阅读体验：** 字体、背景、行距、段距、简繁转换与多种翻页模式可调；支持音量键和自定义物理按键翻页。
* **阅读记录：** 时间线、阅读热力图、读书日历、时长排行与阅读总览统计。
* **漫画与有声：** 漫画阅读、有声书与朗读控制，提供更完整的媒体播放界面。
* **内容处理：** 替换净化、字典、章节翻译、**AI 净化** 与 **AI 改写**，支持自定义翻译提示词。
* **书架与平板：** 多种书架布局、平板端界面优化、书籍备注与伴生分组（自动归类已读/未读）。
* **发现与订阅：** 自定义书源、RSS 订阅、发现页与全文搜索。

---

## 📥 下载与安装

* 从本仓库的 [GitHub Releases](../../releases) 下载最新 APK。
* 发布为**单包**形态，按 ABI 提供 `universal`、`arm64-v8a` 与 `armeabi-v7a` 三个版本，按设备选择即可。
* 支持 Android 8.0 及以上。覆盖安装前建议先通过 WebDAV 或本地备份导出阅读数据。
* 本项目不是官方版本，包名、签名与更新节奏均可能与官方版不同。

---

## 🛠️ 核心功能

1. **多格式本地阅读：** 支持 TXT、EPUB、PDF、UMD、MOBI/AZW3，智能扫描并导入本地文件。
2. **自定义内容源：** 通过书源规则聚合小说、漫画、音频等内容，并支持 RSS 订阅。
3. **高度自定义：** 字体、背景、行距、段距、加粗、简繁转换、翻页动画与阅读方向均可配置。
4. **净化与替换：** 去除广告和无关内容，支持正则替换、字典查询与正文处理。
5. **朗读与听书：** 支持本地 TTS、在线朗读引擎、媒体通知与播放控制。
6. **数据管理：** 支持本地备份、WebDAV 备份与跨设备数据同步。
7. **完全开源：** 无广告，遵循 GPL-3.0，欢迎提交 Issue 与 Pull Request。

---

## 从源码构建

首次检出后运行 `git submodule update --init modules/miuix modules/coui modules/mochi`。使用 JDK 21、Python 3.11+，在 `local.properties` 配置 `sdk.dir` 或设置 `ANDROID_HOME`，安装 Android 36、37.0、37.1 平台后执行：

```shell
python scripts/prepare-ui-forks.py
```

这会将固定提交的 COUI、Mochi 构建到本项目 `build/ui-forks/maven`，随后可照常运行 Gradle；Miuix 继续通过 `includeBuild` 编译。GitHub 的验证、Release 和手动 APK 工作流自动执行相同步骤，不需要 Maven 发布凭据。具体更新流程见 [UI fork 构建说明](docs/dev/ui-fork-builds.md)。

---

## 💬 问题反馈

* 请在本仓库提交 [GitHub Issues](../../issues)。

---

## ❤️ 致谢

### 原项目与参考资料

本项目的代码基础、规则体系与产品形态来自以下原项目；README 的功能整理与界面设计也参考了它们的公开资料：

* [gedoor/legado](https://github.com/gedoor/legado)：原项目，提供书源、阅读器、数据模型与规则体系等基础实现。
* [HapeLee/legado-with-MD3](https://github.com/HapeLee/legado-with-MD3)：本分支直接参考并持续同步的
  Material Design 3 上游分支。
* [Legado 书源教程](https://mgz0227.github.io/The-tutorial-of-Legado/)：书源规则与使用说明参考。

### 开源项目

感谢以下优秀开源项目提供的灵感与技术支持：

* [gedoor/legado](https://github.com/gedoor/legado) (这个项目最吊的老爹)
* [Luoyacheng/legado](https://github.com/Luoyacheng/legado) (提供了更多的扩展功能)
* [rimvoid/legado](https://github.com/rimvoid/legado) (提供了更多的扩展功能)
* [ItosEO/COUI-Kit](https://github.com/ItosEO/COUI-Kit) (使用 Compose 复刻和优化 ColorOS 17 UI 风格的UI库)
* [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix) (使用 Compose 复刻和优化 HyperOS 4 UI 风格的UI库)
* [feice11/mochi-compose](https://github.com/feice11/mochi-compose) (软、弹、跟手的 Jetpack Compose 组件库)
* [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) (适用于 Compose Multiplatform 的可自定义 Liquid Glass 特效库)
* [komikku-app/komikku](https://github.com/komikku-app/komikku) (提供了界面灵感与漫画相关实现参考)
* [FoedusProgramme/Gramophone](https://github.com/FoedusProgramme/Gramophone) (提供了界面灵感)
* [jordond/MaterialKolor](https://github.com/jordond/MaterialKolor) (基于 Jetpack Compose
  优秀的取色实现)
* [Calvin-LL/Reorderable](https://github.com/Calvin-LL/Reorderable) (基于 Jetpack Compose
  优秀的拖动排序实现)
* 以及更多开源项目...

---

## 📄 许可证

本项目基于 [GNU General Public License v3.0](LICENSE) 发布。你可以自由使用、修改和分发，
但衍生作品需继续遵循 GPL-3.0，并保留相应的版权与许可声明。

---

## ⚠️ 用户协议与免责声明

> **【特别提示】**
> 在下载、安装或使用本软件前，请您务必仔细阅读并充分理解本协议及免责声明的全部内容。您一旦下载、安装或使用本软件，即视为已阅读、理解并同意接受本声明的全部内容。
> 联系方式：kudomagaz@gmail.com

本项目为非官方第三方分支，与 Legado 原作者及上游维护者不存在隶属、授权、赞助或背书关系，
项目内容不代表原作者立场。

### 一、软件性质说明

1. 本软件是一款用户可配置的本地化网页内容浏览工具，提供网页访问、内容解析、文本提取、阅读排版与数据管理等技术功能。
2. 本软件默认状态下不预置、不内置、不提供任何第三方网站内容、数据资源或解析规则。
3. 本软件开发者不提供任何内容运营、内容存储、内容发布或内容传播服务。
4. 用户可基于自身需求，自行配置或导入第三方规则，以实现对公开网页内容的个性化浏览与处理。

### 二、用户行为与规则说明

1. 用户可自行创建、编辑、导入或使用第三方分享的解析规则（以下简称“规则”）。
2. 相关规则仅用于定义网页内容的获取、提取与显示方式，其来源、合法性、准确性与适用性均由用户自行判断并承担责任。
3. 用户使用规则访问第三方网站时，相关网络请求由用户设备直接向目标网站发起并接收数据。本软件仅提供本地解析与展示能力，不对第三方网站内容进行修改、编辑或再分发。
4. 用户应遵守所在地法律法规、网络安全要求以及相关网站的服务协议与版权规范，不得利用本软件从事侵犯知识产权、非法传播、未授权数据获取、破坏网络服务或其他违法违规行为。

### 三、第三方内容与社区说明

1. 任何由第三方建立或维护的规则分享平台、论坛、交流群组、网站或其他社区，均属于独立运营的第三方平台，与本软件开发者无隶属关系。
2. 开发者不参与第三方规则、内容或社区的制作、发布、运营、维护与传播行为，亦不对相关内容承担主动审查义务。
3. 用户因使用第三方规则或访问第三方网站而产生的风险，包括但不限于版权纠纷、数据安全风险、网络访问风险或其他法律风险，应由相关行为主体依法承担相应责任。

### 四、隐私与数据说明

1. 本软件主要功能运行于用户本地设备，不设立用于提供网页内容服务的自有内容服务器。
2. 本软件不会主动收集、上传或存储用户的阅读内容、规则列表、浏览记录或其他个人隐私数据。
3. 为提升软件稳定性与兼容性，本软件可能集成第三方统计或崩溃分析服务（如 Firebase Crashlytics
   等），用于收集匿名化的崩溃日志、性能信息及基础设备信息。
4. 部分网络、存储或同步权限，仅用于实现用户主动启用的本地备份、WebDAV 同步或跨设备数据同步等功能。

### 五、知识产权保护

1. 开发者尊重并保护知识产权权利人的合法权益，并反对任何侵犯著作权、商标权或其他合法权益的行为。
2. 用户应确保其使用本软件获取、处理或访问相关内容的行为符合适用法律法规及权利要求。
3. 若权利人认为某些第三方规则存在涉嫌侵权情形，可依法向相关内容实际托管方主张权利。
4. 权利人亦可向开发者提交包含身份证明、权属证明、具体规则信息及相关说明的有效通知。开发者将在合理技术能力范围内，对涉嫌违规规则采取必要处理措施。
