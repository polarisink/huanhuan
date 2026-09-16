# 欢欢

使用 JavaFX 开发的本地桌面软件，用于入出院记录时效统计。导入样例格式的 XLSX 后，输出一个新的 XLSX，包含处理明细和科室统计两个工作表。应用图标使用用户提供的兔子照片。

## 使用

1. 选择待处理的 XLSX 文件。
2. 如需更改保存位置，打开右上角“设置”选择目录并保存；默认使用下载目录，之后自动记住选择。
3. 点击“开始处理”。完成后可打开结果文件或输出目录。

输入文件保持不变。输出名称为 `时效统计_yyyyMMddHHmmss.xlsx`，同名时自动追加序号。数据不上传网络。

入院、出院间隔均以**小时**输出，R、T 表头标注“间隔（小时）”，保留 9 位小数。严格大于 24 小时为不合格，恰好 24 小时、零间隔和负间隔均合格；统计、排序和标红使用未舍入的真实时差。

遇到非法时间或空科室，界面显示原始 Excel 行号、列名和原因，中止处理，不生成结果文件。这里的行号包含表头行，不是 A 列序号。修正文件后可重新处理。

## 开发环境

- JDK 17。
- JavaFX 17.0.20、Apache POI 5.5.1。
- Maven Wrapper 固定 Maven 3.9.11，无需另装 Maven；首次运行需要联网下载构建依赖。
- Windows 7 SP1 / 10 / 11 x64、macOS 11+ ARM64、macOS 10.15.7+ x86_64。

Windows 7 使用 **BellSoft Liberica JDK 17 Full（Windows x64，含 JavaFX）**，推荐 `17.0.20+10`。不能用任意 JDK 17 代替：旧系统兼容性同时取决于运行时、JavaFX 原生库和启动器。最低基线为 Windows 7 SP1 x64，macOS 分别为 ARM64 11+、Intel x86_64 10.15.7+。依据：[BellSoft 支持矩阵](https://bell-sw.com/pages/supported-configurations/)、[Launch4j 官方说明](https://launch4j.sourceforge.net/)。

将 `JAVA_HOME` 指向 JDK 17。macOS 示例：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./mvnw javafx:run
```

Windows 使用 `mvnw.cmd javafx:run`。在 IntelliJ IDEA 中打开 `pom.xml`，将项目 SDK 和 Maven Runner JRE 均设为 JDK 17。

## 验证

```bash
./mvnw test
./mvnw -DsampleVerification=true test
./mvnw -DuiTests=true -Dtest=MainViewSmokeTest test
```

- 核心测试覆盖补全优先级、真实时差、负间隔、24 小时边界、排序与底色、科室分子和分母、无数据占位符、错误行号及同名输出。
- 样例验证读取 `files/` 原件，将结果写入 `target/sample-verification/`，校验原件 SHA-256 未改变。该目录仅用于本地验证，不应公开其中的明细。
- 界面测试需要图形桌面，会验证报错后修正再处理的流程，并在 `target/ui-verification/` 保存界面截图。

## 打包

在对应操作系统上使用对应架构的 JDK 17 打包。原始样例、测试结果和患者明细均不会进入安装包。

macOS 提供本地打包脚本，Windows 同时支持本地打包与 GitHub 标签自动发布。以下命令用于重新打包，脚本会完成构建、测试、运行时组装和对应平台打包。

### macOS

最简单的方式：在 Finder 中双击项目根目录的 **`打包Mac.command`**，选择 ARM64、Intel x64 或两个版本。

也可以在项目目录使用短命令：

```bash
bash scripts/package-macos.sh arm64   # Apple Silicon 版
bash scripts/package-macos.sh x64     # Intel 版，自动使用 Rosetta
bash scripts/package-macos.sh all     # 依次生成两个版本
```

不传参数时生成当前 Mac 的原生版本。生成 `dist/欢欢-1.0.1-macos-arm64.dmg` 或 `dist/欢欢-1.0.1-macos-x86_64.dmg`，附带 Java 运行时并使用照片图标。双击 DMG 后将“欢欢.app”拖到 Applications。`all` 会先检查两套环境，再串行构建，两个结果互不覆盖；请勿同时启动多个打包任务。

脚本自动查找架构匹配的 JDK 17：先检查 `JAVA_HOME`，再检查项目的 `.tools/jdk17-arm64` / `.tools/jdk17-x86_64`，最后查找系统及用户 Java 安装目录。`JAVA_HOME` 版本或架构不匹配时会继续查找，不会把 ARM JDK 用于 Intel 包。JDK 根目录应包含 `bin/java`，也支持 `.jdk/Contents/Home` 结构。

本机两套 JDK 已放入项目 `.tools/`，该目录已被 Git 忽略；移到其他机器时需另外准备 JDK。脚本不会自动下载安装 JDK、安装 Rosetta 或更改全局 Java 配置。Apple Silicon 打包 x64 需要已安装 Rosetta；Intel Mac 只能执行 x64 工具链。

只检查环境而不构建：

```bash
bash scripts/package-macos.sh all --check
```

特殊目录也可分别通过 `HUANHUAN_JDK17_ARM64`、`HUANHUAN_JDK17_X64` 指定；显式指定的路径不合法时会直接报错。

当前脚本生成本地测试包，未配置 Apple Developer ID 签名与公证。对外分发前需使用发布者证书签名、公证；本地下载包首次打开时可能需要在系统安全设置中允许打开。

### Windows

打包机使用 Windows 10/11 x64、PowerShell 5.1 及以上；目标 Windows 7 机器只需解压运行 EXE，无需安装 PowerShell 或 Java。

1. 在 [BellSoft 下载页](https://bell-sw.com/pages/downloads/)选择 JDK 17、Windows、x86、64 bit、**Full** 包并解压（推荐 `17.0.20+10`，含 JavaFX 17）。
2. 将下面路径改为解压后包含 `bin`、`jmods`、`release` 的 JDK 根目录。脚本会检查厂商、版本、架构及 JavaFX 模块。

```powershell
powershell -ExecutionPolicy Bypass -File scripts/package-windows.ps1 -JdkHome 'C:\Java\bellsoft-jdk17.0.20-full'
```

生成 `dist/欢欢-1.0.1-windows-x64.zip`。解压整个文件夹后双击 `欢欢\欢欢.exe`，无需安装 Java。EXE、app 和 runtime 目录需一起保留；EXE 不是单文件程序。

脚本使用 Liberica 自带的 JavaFX 模块运行测试及组装运行时，移除 Maven 下载的 JavaFX JAR，再由 Launch4j 生成带照片图标的 EXE。Launch4j 及其构建工具由 Maven 自动下载，无需另装 Launch4j 或 WiX。启动器只使用随包的 `runtime`，不要求用户配置系统 Java。

Windows 7 首次验收需检查：普通用户双击 EXE、中文及含空格路径、选择文件与保存目录、非法数据报错、正常导出两个工作表。若系统缺少运行时 DLL，应按 BellSoft 对该发行版的要求补齐系统更新；本项目未在 Windows 7 实机上完成此验收。

### GitHub 自动发布 Windows 10 x64

推送任意标签会触发 `.github/workflows/windows-release.yml`。例如：

```bash
git tag v1.0.2
git push origin v1.0.2
```

发布前将 `pom.xml` 中的应用版本更新为对应版本；Windows 打包脚本从该文件读取版本号。工作流使用 Windows Server 2022 x64 构建机、固定的 Liberica JDK 17.0.20+10 Full，执行自动化测试、生成 EXE、验证主窗口启动，成功后创建标签对应的 GitHub Release，附上 `huanhuan-win10-x64.zip` 和 `SHA256SUMS.txt`。目标为 Windows 10 x64，构建机启动检查不能代替 Windows 10 实机验收。发行包未做代码签名。

下载 ZIP 并完整解压后运行 `欢欢/欢欢.exe`；EXE 需要同包的 `app` 和 `runtime` 文件夹。也可以在 GitHub Actions 的 **Windows x64 Release → Run workflow** 手动验证构建：选择分支时只提供构建产物，选择标签时同时发布 Release。相同标签重新运行时更新 Release 附件，不重复创建 Release。

自动发布使用 GitHub 自动提供的 `GITHUB_TOKEN`，无需配置个人令牌；只有发布任务有 `contents: write` 权限。JDK Full 的配置依据：[setup-java 官方说明](https://github.com/actions/setup-java/blob/main/docs/advanced-usage.md)。

`files/` 原始样例、`target/` 测试结果、`dist/` 安装包和 `.tools/` 本机工具均不提交 Git。默认测试使用生成的模拟数据；本地样例验证需要自行保留原来的 `files/` 目录。

### 图标资源

- 原始照片：`packaging/icons/source.jpg`。
- JavaFX 界面与窗口图标：`src/main/resources/icons/huanhuan.png`。
- macOS 应用图标：`packaging/icons/huanhuan.icns`。
- Windows EXE 图标：`packaging/icons/huanhuan.ico`，包含 16、32、48、64、128、256 像素版本。

图标已转换并包含在项目中，运行打包脚本无需额外图像处理工具。

### 内置中文字体（1.0.1）

主界面、设置、提示框与路径提示统一使用随包的 Noto Sans CJK SC 2.004 常规体和粗体，启动时通过 `Font.loadFont` 注册，仅在应用进程内使用，不安装系统字体。字体文件、来源和 SIL OFL 1.1 许可位于 `src/main/resources/fonts/`，会随应用一同打包。

此变更针对 Catalina 上常规字重显示异常的反馈。原样式使用了 JavaFX 不支持的逗号分隔字体列表，现改为明确的单一字体家族。相关依据：[JavaFX CSS 字体限制](https://openjfx.io/javadoc/17/javafx.graphics/javafx/scene/doc-files/cssref.html)、[Noto CJK 官方字体](https://github.com/notofonts/noto-cjk/tree/Sans2.004)。界面测试会检查常规体、粗体和设置窗口实际解析到的字体，并生成截图；Catalina 的实际显示仍需在目标机器验证。

## 代码结构

| 位置 | 用途 |
| --- | --- |
| `src/main/java/cn/huanhuan/core` | 校验、时间补全、计算、排序、统计及保存 |
| `src/main/java/cn/huanhuan/app` | JavaFX 界面与后台任务 |
| `src/main/java/cn/huanhuan/platform` | 系统下载目录查询 |
| `src/main/resources/styles/app.css` | 界面样式 |
| `src/test` | 核心、样例与界面验证 |
| `scripts` | 各平台打包脚本 |

处理时先校验全部输入，再在内存中更新数据。临时工作表用于安全重排整行，导出前移除；统计表直接加入同一工作簿。输出先写临时文件，完整写入后再发布最终文件。

## 平台验证状态

- 本机 macOS 26.5.2 ARM64：使用 Liberica JDK 17.0.20+10、JavaFX 17.0.20 运行 `clean test`（启用样例及界面验证），25 项测试全部通过。编译产物为 Java 17 字节码（major version 61）。
- 原始样例：3,413 条记录，27 条存在至少一项超时，25 个科室；已独立读取 OOXML 复核所有原有业务字段、补全时间、间隔、排序与科室合格率，原件 SHA-256 未改变。
- 此前版本已验证 macOS ARM64 打包启动；`dist/` 中旧安装包不包含本次 Java 17 / Windows 7 兼容性调整，不应用于验证新版。
- 已核对 BellSoft 官方 Windows Full ZIP 内的发行版信息与 JavaFX JMOD 文件；macOS 打包脚本通过 shell 语法检查。
- macOS x64 DMG：在 Apple Silicon 上通过 Rosetta 和独立的 Liberica JDK 17.0.20+10 x64 生成。构建测试通过；39 个应用/运行时 Mach-O 文件及 7 个 JavaFX 动态库均确认为 x86_64，应用签名校验通过，使用包内运行时的界面流程测试通过。DMG 完整性校验通过，从只读挂载的 DMG 直接启动 8 秒无报错；测试进程已退出，临时挂载已卸载。未更改系统 Java 安装或全局配置。
- Catalina 修正：此前 x64 包的 `LSMinimumSystemVersion` 设为 11.0，导致 macOS 10.15.7 在启动前被系统拦截。Intel 版现改为 10.15.7，ARM64 版仍为 11.0；已检查当前 x64 原生文件的构建基线：启动器/Java 为 10.12、JavaFX 为 10.10、JNA 为 10.3。此检查可排除已声明的最低系统版本冲突，但不等同于 Catalina 实机验证。请重新下载 DMG 并替换旧应用。
- 1.0.1 字体修正：已验证字体和许可包含在应用 JAR 中；使用 x64 包内运行时的界面测试通过，主界面常规体/粗体、设置路径实际使用 Noto 字体，本机截图中文显示正常。此结果尚不能代替用户的 macOS 10.15.7 实机复测。
- Windows 7 SP1 / 10 / 11 x64、macOS Intel 及最低系统版本仍需在对应机器上验证，不能由脚本配置或本机测试代替。

完整业务规则见 [需求文档](需求文档.md)。
