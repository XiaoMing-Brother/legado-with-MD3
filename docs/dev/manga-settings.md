# 漫画全局设置

入口为“设置 → 漫画设置”，与阅读界面设置并列。Navigation 3 key 为 `MainRouteSettingsManga`，
普通设置页参与宽窗口双栏，并可通过 `settings/manga` 恢复入口。页面 owner 为
`feature/settings/manga`；现有漫画阅读器保留快捷设置，不移动成熟渲染、手势或会话代码。

## 配置范围

- 漫画浏览开关从“其他设置”移至此页，仍使用已有 `showMangaUi` 偏好键，不另存一份。
- 阅读布局：默认阅读方式、默认条漫侧边留白，以及全局缩放、宽页、双页和章节首尾提示。
- 阅读操作：捏合缩放、点击翻页、图片长按菜单、音量键／反向音量键和自动阅读速度。
- 图片显示：翻页动画、图片淡入、自动／固定背景、灰度、墨水屏及阈值。
- 预加载与缓存：当前章原图预加载、自动离线缓存及前后章节数；提供下载与缓存设置入口。

设置项与“阅读界面”统一使用 `SwitchSettingItem`、`DropdownListSettingItem`、`SliderSettingItem`、
`ClickableSettingItem` 和 `SplicedColumnGroup`，不用 Tiny 系列。滑块沿用正式设置组件的松手提交、
输入数值和恢复默认值交互。

阅读方式和条漫留白有按书覆盖，新入口只修改缺省值，不覆盖书籍 ReadConfig。
章节预缓存数量继续通过 `ReadSettingsGateway` 修改，与正文共用；不使用已不参与当前会话的
`MangaSettings.chapterPrefetchCount` 作为第二来源。全局漫画参数使用 `MangaSettingsGateway.update`。

灰度和墨水屏互斥，切换时在同一次 `copy` 中提交；滑块只在松手／步进完成后写入，拖动值留在 UI。
页面收集 Gateway 的 Flow，阅读器与设置页共享同一配置来源。分组没有 bookUrl，也不会读取
书籍锚点、创建空书配置或发起翻译请求。错误保存在 UiState 中，取消不当作写入失败。

## 已下线内容

2026-11-02 起漫画 AI 翻译整体移除：本页不再包含“漫画翻译”分组、语言／模型／OCR／擦字／排版选项，
也不再有 AI 供应商或模型入口；`MainRouteSettingsManga` 的 `openTranslation` 定位参数同步删除。
历史翻译数据（译图缓存、术语表）不再由 UI 读写，交由后续数据清理决策处理。

## 验证范围

使用 JDK 21，执行 `:app:testAppCoreDebugUnitTest :app:assembleAppCoreDebug :app:compileAppCoreDebugAndroidTestKotlin verifyConfigArchitecture`。
