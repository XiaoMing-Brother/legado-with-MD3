# iOS curl mesh kernel

这个包由两类文件混合组成，改动前先确认自己改的是哪一类。

## 生成文件（不要手改）

以下文件头部都带 `// Generated curl mesh algorithm stages.`，由算法阶段的数据流分析产出，
手改会在下一次重新生成时被覆盖，且很容易让「槽位复用」与算法阶段失配：

- `KernelValues.kt`（`CurlMeshKernel` 的语义视图，槽位索引描述内存复用，不是算法常量）
- `KernelDrawing.kt`、`KernelFaceSampling.kt`、`KernelFlow.kt`、`KernelPenumbra.kt`、
  `KernelProjection.kt`、`KernelShadowTopology.kt`、`KernelSurfaceSampling.kt`

需要改算法时改阶段数据源后整体重生成，不要局部编辑。仓库内目前没有提交生成脚本，
所以这些文件在生成源落地前视为冻结：只能读，不能改。

## 手写文件

`CurlMeshKernel.kt`、`KernelInput.kt`、`KernelLayout.kt`、`KernelMemory.kt`、
`KernelPrimitives.kt` 是手写实现：内存 arena、布局常量、输入归一化与基础图元。
要加新能力（新图元、新布局字段）改这里，再由算法阶段消费。

## 边界

- `KernelMemory` 是托管 arena，地址只是数组下标，**不是** native 指针，不能传给 JNI。
- 所有槽位共享同一块 `LongArray`；`KernelValues` 的槽位编号一旦改动，所有读写方必须同步。
- 这个包只做几何计算，不得依赖 Compose、Android View 或资源。
