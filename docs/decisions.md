开发决策记录 (Decisions)

本文件记录项目开发过程中的关键技术取舍、踩坑记录及底层原理。


## D-01 端口冲突与 Docker 端口映射取舍 (2026-10-25)

问题描述：启动 Docker Compose 时，MySQL 容器报错 bind: Only one usage of each socket address。

原因分析：宿主机本地已安装了 MySQL 服务，且正在占用默认的 3306 端口。

候选方案：1. 强杀本地 MySQL 进程，腾出 3306 端口；2. 修改 Docker Compose，将宿主机的 3307 映射到容器的 3306。

最终决策：选择方案 2。修改 docker-compose.yml 中的 ports: - '3307:3306'，并在 Spring Boot 的 application.yml 中同步修改数据库连接端口。

放弃方案原因：保留本地 MySQL 可以避免影响本机其他可能依赖数据库的软件或日常练习。

面试话术：“在本地开发时我遇到了端口冲突，我选择修改 Docker 映射而不是强行杀掉本地进程，这样能保证依赖环境的隔离性，只改动项目配置即可。”


## D-02 Windows 下文件编码导致的 BOM 头坑 (2026-10-25)

问题描述：Maven 编译时，Java 启动类报错 非法字符: '\ufeff'。

原因分析：在 Windows 下使用 PowerShell 或旧版记事本生成文件时，默认保存的 UTF-8 带有 BOM（Byte Order Mark）头，Java 编译器严格校验，无法识别该隐藏字符。

候选方案：1. 写一段代码/脚本去消除 BOM 头；2. 使用现代文本编辑器（或指定无 BOM 的另存为）重写该文件；3. 在 Maven 中强制全局指定编码。

最终决策：删掉原文件，用编辑器保存为纯净的 UTF-8 (无 BOM)，并在父 pom.xml 显式指定 <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding> 强制规范。

放弃方案原因：从源头解决，避免后续所有生成文件都去扫描 BOM 头。

面试话术：“这让我意识到跨平台开发时文件编码的重要性。后来我在 Maven 里显式指定了 UTF-8，确保在任何环境下编译都不会出现乱码或不兼容字符。”


## D-03 JDK 21 的 Maven 编译环境被旧路径污染 (2026-10-25)

问题描述：系统终端里 javac -version 和 mvn -version 都显示 JDK 21，但在 Maven 编译时持续报错 Fatal error compiling: 错误: 不支持发行版本 21。

原因分析：系统环境变量 Path 中存在一个残缺的 D:\bin 路径，导致 Maven 底层调用编译器时，找到了错误的、不完整的 Java 环境。

候选方案：1. 修改系统级环境变量并修复 JDK 目录结构；2. 使用 Maven 的 toolchains.xml 强制指定编译使用的 JDK 绝对路径；3. 在 pom.xml 中显式指定新版 maven-compiler-plugin 并配置 <release>21</release>。

最终决策：全套使用（方案 1+3）。下载了纯净的 JDK 21 解压到 D:\Dev，清理了环境变量中的脏路径，并在父 pom.xml 中锁死了编译插件版本 3.13.0 与 <release>21</release> 参数。

放弃方案原因：优先从系统层面修复，一劳永逸；配合 pom.xml 锁版本，确保团队协作（或换电脑）时依赖一致。

面试话术：“排查环境问题让我对 Java 的类加载和运行机制有了更深的理解。我不仅在系统层修了环境变量，还在 Maven 构建中显式指定了编译器版本和 release 参数，确保构建过程不依赖开发者的偶发环境，做到了构建环境的隔离与稳定。”


## D-04 MyBatis-Plus 3.5.17 的底层依赖拆分坑（2026-10-26）

问题描述：引入 mybatis-plus-spring-boot3-starter 后，代码飘红，找不到分页插件 PaginationInnerInterceptor 和基类 IService。

原因分析：官方在 3.5.9+ 版本为了轻量化，把分页解析包和部分服务层基类拆分了。内置的 starter 不再包含这些依赖，必须显式引入。

候选方案：1. 引入缺失的 extension 和 jsqlparser 并继承 ServiceImpl；2. 引入分页插件包，同时放弃继承 ServiceImpl，直接在 Service 层注入 UserMapper 编写逻辑。

最终决策：选择方案 2。引入 mybatis-plus-jsqlparser:3.5.17 解决分页报错，在 UserServiceImpl 中直接注入 UserMapper。

放弃方案原因：新版框架包路径变动频繁，强行继承容易引发复杂的依赖冲突。直接注入 Mapper 代码更轻量，脱离了 MP 的强绑定。

面试话术：“在使用最新版 MyBatis-Plus 时，我遇到了依赖拆分导致的类找不到问题。我没有死磕框架的内部基类，而是选择直接注入 Mapper 来解耦，这不仅解决了报错，也让我对 ORM 框架的本质有了更深的理解。”


## D-05 Spring Boot 3.5.5 与 Knife4j 4.5.0 的版本冲突（2026-10-26）

问题描述：项目启动后，访问 /v3/api-docs 或文档页面直接返回 500，控制台报错 NoSuchMethodError。

原因分析：Knife4j 自带的底层依赖 springdoc-openapi:2.3.0 版本太老，与 Spring Boot 3.5 底层的 Spring Framework 6.2 存在 API 不兼容。

候选方案：1. 降低 Spring Boot 版本；2. 排除 Knife4j 自带的旧版 springdoc，手动引入最新版；3. 放弃 Knife4j 改用原生 Swagger UI。

最终决策：选择方案 2。在 pom.xml 中排除 Knife4j 默认的 springdoc 依赖，手动引入 2.8.13 版本。并在 application.yml 中设置 knife4j.enable 为 false 规避增强功能的冲突。

放弃方案原因：降级框架不符合技术演进趋势；原生 Swagger UI 缺少中文环境和更好的 UI。

面试话术：“在整合 API 文档时，我通过查看源码堆栈，定位到是 Spring Boot 3.5 底层升级导致第三方组件调用了废弃方法。我通过 Maven 的 exclude 标签进行依赖仲裁，替换了兼容版本，这锻炼了我阅读源码和处理 Jar 包冲突的能力。”


## D-06 JDK 21 的编译环境与 IDEA 配置对齐（2026-10-26）

问题描述：Maven 编译时持续报错：源发行版 21 需要目标发行版 21。

原因分析：Maven 的编译插件默认版本太低，不认识 21；且 IDEA 的 Project Structure 中 SDK 未正确切换，IDE 编译器与 Maven 编译器未能统一。

候选方案：1. 仅修改环境变量；2. 仅修改 pom.xml；3. 全局彻底对齐。

最终决策：执行方案 3。在父 pom.xml 中锁定 maven-compiler-plugin 为 3.13.0 并配置 release 21；同时在 IDEA 的 Project Structure 和 Java Compiler 设置中，把 SDK 和 Bytecode version 强制统一为 21。

放弃方案原因：只改一处会导致“命令行能跑，但 IDEA 飘红”或“IDEA 能跑，打包报错”的尴尬局面。

面试话术：“升级 JDK 21 后遇到了编译期报错，我意识到这不仅是 POM 文件的问题，更是构建工具与 IDE 之间配置不同步的问题。通过统一两者的字节码版本，彻底解决了编译兼容性。”


## D-07 密码加密包的依赖下载失败与兜底策略（2026-10-26）

问题描述：按照大纲使用 BCrypt 算法，但引入 spring-security-crypto 依赖时，Maven 网络波动导致无法下载，导致代码全线飘红。

原因分析：国内镜像源对该包的拉取存在延迟，且引入整个 Spring Security 体系对于单体项目前期来说过重。

候选方案：1. 停下手头工作死磕 Maven 依赖；2. 完全放弃加密，明文存储；3. 手写基于 JDK 原生的 MessageDigest 实现 SHA-256 加密。

最终决策：选择方案 3。临时在 UserServiceImpl 中手写了纯原生的 SHA-256 哈希方法。优先保证业务主流程跑通。

放弃方案原因：死磕依赖浪费时间，影响项目进度；明文存储严重违反安全规范。后期网络恢复或直接下载 jar 包后，可无缝切回 BCrypt。

面试话术：“开发中遇到依赖无法拉取的问题，我没有被环境卡死。为了不阻塞主流程，我先用原生代码实现了哈希加密作为兜底。当然我清楚生产环境必须用 BCrypt，这体现了我分清主次和兜底解决的开发思路。”

## D-08 题库列表不能把答案直接发给前端（2026-10-28）

问题描述：如果系统直接把数据库里的题目数据原封不动发给前端，那答案也就跟着发过去了，用户只要按个 F12 抓包就能看到答案，这刷题系统就废了。

原因分析：不能相信前端，前端能看到的，用户就一定能看到。

候选方案：1. 照常发所有数据，让前端程序员写代码把答案藏起来；2. 专门建一个给前端展示用的类（把答案字段去掉），只发这个类。

最终决策：选 2。我单独建了一个 QuestionListVO 类，里面只有题目、选项、科目，坚决不写答案和解析这两个字段。

放弃方案原因：前端藏起来没用，别人一抓包就露馅了。必须从后端的源头就把答案掐断。

面试话术：“做题库接口的时候，我专门做了数据脱敏。哪怕前端说‘你给我全量数据我自己藏’，我也坚决只返回该显示的字段。这不仅是为了防作弊，也是为了保证接口的数据安全。”


## D-09 同一个题错两次，错题本不能记两笔账（2026-10-28）

问题描述：用户答错题了，系统要把他拉进错题本。但如果这个题他错了两次，错题本里绝不能出现两条一样的题，只能是错题次数加 1。要是两个请求同时过来，更不能报错。

原因分析：如果用最笨的办法，先查一下有没有，没有再插入，在人多的时候（并发）绝对会出岔子。

候选方案：1. 先查数据库，没有就插入，有就更新；2. 直接在数据库里加个“唯一限制”（同一个人的同一道题只能有一条），然后用数据库自带的语法（如果有就更新，没有就插入）。

最终决策：选 2。在数据库表里加了唯一索引，然后用 ON DUPLICATE KEY UPDATE 这个语法，让数据库自己去判断。

放弃方案原因：方案 1 在人多的时候会出Bug，代码写得再好也防不住同时进来的请求。

面试话术：“错题本防重复，我没用先查后插的笨办法，因为抗不住高并发。我直接用数据库的唯一索引配合 Upsert（有则更新，无则插入）语法。而且我通过判断数据库返回的影响行数，精准识别了‘这道题是不是第一次进错题本’，业务逻辑非常严谨。”


## D-10 要么全干成，要么全拉倒（2026-10-28）

问题描述：用户点交卷，系统要做两件事：1. 记下他答了题；2. 如果错了，把他记进错题本。这两步必须绑在一起，不能答了题却没进错题本。

原因分析：Spring 框架的事务（就是用来保证一起成功或一起失败的机制）有很多坑，有时候报错了它也不会回滚（撤销）。

候选方案：1. 在 Controller（接收请求的层）加事务；2. 在 Service（处理业务的层）的 public（公开的）方法上加事务，并且必须加上 rollbackFor = Exception.class 这个配置。

最终决策：选 2。把事务严格锁在业务层，并且在里面故意写了个报错代码做测试。

放弃方案原因：如果把事务写在 Controller 里，代码一旦复杂就很难管。必须写在真正处理业务的方法上。

面试话术：“为了保证答题记录和错题本的数据一致，我把事务边界严格卡在 Service 层。而且我不仅仅背八股文，我还真的在代码里扔了一个 RuntimeException（运行时异常），去数据库里亲眼确认了数据真的回滚了。我知道如果异常被 try-catch 偷偷吃掉，或者方法自己调自己，事务都会失效，我在写代码时全都避开了。”


## D-11 给数据库建个目录，查题快一点（2026-10-28）

问题描述：学生刷题的时候，经常按‘科目’和‘章节’来筛选。如果表里数据几百万条，每次查都从头找一遍，卡得要死。

原因分析：没建索引（就像书的目录），数据库只能挨个找。

候选方案：1. 不建索引；2. 只建单列索引（比如只给科目建）；3. 建一个‘科目+章节+难度’的联合索引（把这三个绑在一起建目录）。

最终决策：选 3。建了联合索引，这样查起来就会走目录。

放弃方案原因：单列索引不够用，联合索引能最精准地匹配咱们的查询条件。

面试话术：“题库查询我设计了联合索引，并且清楚‘最左前缀’的原则。不过我也知道，如果表里的数据只有几百条，数据库可能会觉得挨个找比看目录还快，所以我打算用 EXPLAIN（执行计划）工具去对比一下建索引前后的真实效果。”


## D-12 千万别用黑框框（命令行）去新建代码文件（2026-10-28）

问题描述：为了图省事，我在 Windows 的那个黑框框（终端）里敲命令新建 Java 文件，结果文件一打开，里面全是乱码，代码疯狂报错飘红，怎么改都不好使。

原因分析：Windows 系统偷偷在文件头加了看不见的脏字符（叫 BOM 头），Java 编译器不认这些东西，觉得是乱码。

候选方案：1. 去弄个脚本把脏字符清掉；2. 彻底不用命令行了，就在开发工具里老老实实右键新建。

最终决策：选 2。把所有报错的文件全删了，在 IDEA 里重新建文件、重新粘代码。并且把项目的编码彻底定死为 UTF-8。

放弃方案原因：治病要治根，以后再也不用这种歪门邪道偷懒了，老老实实点鼠标新建最安全。
面试话术：“开发过程中遇到过一次非常坑的乱码问题，排查了半天才发现是因为我用命令行建文件，导致文件带了 BOM 头。这次经历让我深刻认识到了跨平台开发中，文件编码统一为 UTF-8 的重要性。后来我也在 pom.xml 里强制锁死了编码，彻底解决了这个问题。”

## W2 阶段技术决策记录

## D-13 错题本收录采用唯一索引加 Upsert 保证并发幂等
问题：用户同一题多次答错时，错题本应只保留一条记录并累加错误次数。若采用先查后插逻辑，在并发请求下会产生重复行或报错。
候选方案：1. Service 层先查询，不存在则 insert，存在则 update；2. 依赖数据库 uk_user_question 唯一索引，使用 INSERT ON DUPLICATE KEY UPDATE。
最终决策：选择方案 2。通过返回的 affectedRows（1 为插入，2 为更新）精准判定是否为首次收录，并返回 wrongBookAdded 标识。
放弃原因：先查后插存在竞态窗口，必须在数据库层用唯一索引兜底，确保最终一致性。

## D-14 严格界定事务边界与异常回滚
问题：提交答题需同时写入 user_answer_record 并更新 wrong_book，若中途异常，需保证两张表同成同败。但 Spring 事务在自调用或吞异常时易失效。
候选方案：1. 在 Controller 层加事务；2. 在 Service 层的 public 方法上标注事务注解并指定 rollbackFor = Exception.class。
最终决策：选择方案 2。事务严格锁定在业务层，并在测试用例中主动抛出 RuntimeException 验证回滚。
放弃原因：Controller 仅负责路由，不应包含业务事务。同时已明确规避了同类自调用、异常被 try-catch 吞掉等失效场景。

## D-15 题库筛选查询的联合索引设计与验证
问题：题库需支持 subject、chapter、difficulty 多条件组合筛选，数据量增大时全表扫描会导致性能骤降。
候选方案：1. 不建索引；2. 仅建单列索引；3. 建立 subject、chapter、difficulty 三列联合索引。
最终决策：选择方案 3。在表结构中添加联合索引，并计划使用 EXPLAIN 进行三状态对比测试。
放弃原因：单列索引无法覆盖多条件组合查询，联合索引符合最左前缀原则，能显著减少扫描行数。同时意识到 200 行小表优化器可能走全表扫描，需在压测报告中如实记录。

## D-16 文件编码强制统一为无 BOM 的 UTF-8
问题：在 Windows 下使用终端命令生成 Java 文件时，意外产生 BOM 头，导致 Maven 编译报错非法字符。
候选方案：1. 写脚本全局消除 BOM 头；2. 使用 IDE 等现代工具创建文件，并在 Maven 中锁定编码。
最终决策：选择方案 2。删除报错文件，使用 IDEA 重新创建；同时在父 pom.xml 中配置 project.build.sourceEncoding 为 UTF-8。
放弃原因：从源头规范创建流程，并在构建工具层锁死编码，彻底杜绝跨平台开发时的乱码隐患。

## D-17 错题列表用自定义分页 SQL + 排序白名单
- **问题**：列表要带题干（跨表），且排序字段由前端传。
- **选择**：一条 join 的 SQL + `<choose>` 分支收敛排序字段。
- **放弃**：① 先查错题再逐条查题（N+1 问题）；② 用 `${orderBy}` 拼接（SQL 注入风险）。
- **局限**：`#{subject} IS NULL` 的写法会让优化器放弃索引，但单用户错题本行数极小，可接受。

## D-18 越权删除统一返回 404
- **问题**：用户尝试删别人的错题，返回什么？
- **选择**：404（“资源不存在”）。
- **放弃**：403。因为 403 等于告诉调用方“这个 id 存在，只是不是你的”，存在资源枚举风险。SQL 中使用 `WHERE id=? AND user_id=?` 兜底。

## D-19 重练答对后删除错题记录，不加 status
- **问题**：重练答对后，错题如何标记？
- **选择**：直接从 `wrong_book` 表 DELETE。
- **理由**：历史记录已完整保存在 `user_answer_record` 中，W5 统计不受影响。加 status 徒增表结构复杂度和查询成本。

## D-20 缓存 value 用显式 JSON，不用 GenericJackson2Json
- **问题**：Redis 存对象使用什么序列化器？
- **选择**：`StringRedisTemplate` + 注入的 `ObjectMapper` 显式序列化。
- **放弃**：默认的 `GenericJackson2JsonRedisSerializer`。因为它会往 JSON 里塞 `@class`，泛型反序列化易崩溃且脏。
- **代价**：多写 20 行编解码代码。

## D-21 缓存三件套落点与删缓存时机
- **防穿透**：题目详情查不到时写入 `__NULL__` 哨兵，TTL 5 分钟。
- **防击穿**：缓存重建时用 `SET NX EX` 抢锁 + 双检 + 自旋 3×50ms + 降级。
- **防雪崩**：TTL 设为基础值 ±20% 随机抖动。
- **一致性**：写库后删缓存，且必须挂在 `afterCommit`（防脏数据回填）。Top100 榜单额外做一次 500ms 延迟双删。局限是只能缩小不一致窗口，不能消灭。

## D-22 wrong_book 新增两条索引
- **idx_user_time**：支撑列表默认排序（否则 filesort）。
- **idx_question**：支撑 Top100 的 `GROUP BY question_id`（原唯一索引最左前缀用不上）。
- **局限**：小表上优化器可能全表扫，`key=NULL` 不代表索引无效。

## W4 阶段技术决策记录

## D-23 RabbitMQ 拓扑结构：主队列 + TTL 重试队列 + DLQ
- **问题**：AI 报告生成属于长耗时任务，且大模型接口可能偶发失败，需要异步处理、失败重试、重试次数可数，且不能无限循环堵死队列。
- **选择**：Direct 交换机 + 三个队列（主队列、TTL 重试队列、死信队列）。消息消费失败后不直接 nack，而是手动投递到 TTL 队列，靠 DLX 到期死信路由回主队列。
- **放弃**：① 单队列无限 requeue。因为 requeue 无法修改消息头，无法记录重试次数，一个毒消息会占满整个队列；② Spring Retry 内存重试。因为进程崩溃时内存状态全丢，且会长时间占住消费线程。
- **局限**：TTL 的精度受 RabbitMQ broker 到期扫描机制影响，15s 的重试间隔可能变成 16-18s，但业务上完全可接受。

## D-24 两层重试策略：AI 侧调用级重试，基础设施侧消息级重试
- **问题**：大模型接口超时（通常需要 5-30 秒）或限流时，如果交给 MQ 去重试 3 次，用户要等 90 秒，并且会白白消耗 3 倍的 token 费用。
- **选择**：分层重试。**调用级**（由 Spring AI 负责）：遇到大模型读超时、429、5xx 错误，最多重试 2 次（退避 1s、2s），失败立刻降级并 ACK；**消息级**（由 MQ 负责）：只有遇到 MySQL 宕机、代码 NPE 等非预期异常才重投 MQ。
- **放弃**：全部交给 MQ。因为 AI 超时重投属于“业务上已经处理成功”（用降级兜底了），把它扔回队列会造成资金和时间的双重浪费。
- **验证**：故意写错 API Key，控制台瞬间打印降级日志，接口依然返回 200 且 `status=2`。

## D-25 重试机制用「手动重投 + ACK」代替 nack(requeue=false)
- **问题**：原生 MQ 的 `nack` 无法修改消息头。如果不修改消息头，怎么记录当前是第几次重试？
- **选择**：手动 publish 消息到重试队列，在消息头里加上 `x-retry-count`（自增），然后再对原消息发送 ACK。
- **放弃**：① nack + 队列死信路由。因为无法计次，只能无限重试；② 在数据库加 retry_count 字段。因为把 MQ 的运行态塞进业务表，会增加数据库无谓的写压力。
- **代价**：可能出现“publish 成功但 ACK 失败”导致消息重复投递。但这正好被我们的业务幂等逻辑（D-22）完美兜底覆盖了。

## D-26 消费者幂等兜底：查状态拦截 + 条件更新防重
- **问题**：RabbitMQ 保证的是“至少投递一次（At-Least-Once）”，网络抖动或重投会导致重复消费，绝对不能重复扣 AI 的 token。
- **选择**：消费前先按 `reportId` 查数据库状态。如果 `status != 0`（说明已经是 1 成功或 2 降级），打印 `skip duplicated` 日志并直接 ACK 丢弃。落库时，SQL 带上 `WHERE id=? AND status=0` 条件。
- **放弃**：只靠数据库唯一索引。因为唯一索引只能防插入，不能防更新。
- **验证**：在 RabbitMQ 控制台手动伪造一条已完成的消息投递进队列，控制台打印跳过日志，数据库的 `token_cost` 和 `finish_time` 纹丝不动。

## D-27 60 秒限流：SET NX EX 单命令，放弃 INCR + EXPIRE
- **问题**：用户狂点生成按钮，需要限制同一用户 60 秒内只能触发 1 次。
- **选择**：使用 Redis 的 `SET key value NX EX 60` 命令。Value 存本次的 `requestId`。
- **放弃**：① `INCR` + `EXPIRE`。因为两条命令非原子，如果 INCR 成功后服务宕机，这个 key 就永不过期了，导致用户被永久封禁；② Lua 脚本。因为单命令已满足原子性，没必要写脚本。
- **细节**：如果之后投递 MQ 失败，必须主动 `delete` 这个限流 key，不能因为系统的故障让用户被白白罚站 60 秒。

## D-28 降级记录留痕：ai_report 增加 model 与 error_msg 列
- **问题**：大模型调用失败触发了降级，写了模板报告。但事后排查时，怎么知道当时是因为 401 鉴权失败、还是因为网络超时、还是因为没有数据？
- **选择**：在 `ai_report` 表增加 `model`（区分是 deepseek-chat 还是 template-v1）和 `error_msg`（记录 401、timeout、no_data 等摘要）两列。
- **放弃**：只打日志。因为日志会轮转（Log Rotation），一周后就查不到了；单独建一张日志表又过于沉重。记在业务表里，方便直接写 SQL 做降级率统计（如：`select count(*) from ai_report where status=2`）。

### W5 踩坑记录

**踩坑 1：Redis 宕机导致整个业务接口卡死超时（Lettuce 默认超时机制）**
- **现象**：为了验证降级，我手动停掉了 Redis，结果发现接口并没有立刻去查 MySQL，而是卡了非常久，最后报了 `ReadTimeoutException`。
- **原因**：Spring Data Redis 底层使用的 Lettuce 客户端，默认的连接和命令执行超时时间非常长（60秒）。它一直傻等 Redis 恢复，导致 Tomcat 线程被长时间阻塞，这在高并发下会直接拖垮整个服务。
- **解决**：在 `application.yml` 中配置 `spring.data.redis.timeout=2s`，实现快速失败（Fail-Fast）。
- **面试话术**：“我做过缓存降级测试，发现仅仅写 try-catch 是不够的。因为 Redis 客户端默认超时时间很长，如果缓存雪崩或宕机，请求会卡在等连接上，导致 Tomcat 线程耗尽。所以我显式配置了 2 秒超时，配合降级代码，保证主链路的高可用。”

**踩坑 2：拦截器抛异常拦截了缓存降级**
- **现象**：配置了 2 秒超时后，再次停掉 Redis 测接口，接口直接返回了 500 `RedisConnectionFailureException`，根本没有走到我写的 `StatService` 里的降级代码。
- **原因**：因为项目配置了鉴权拦截器 `LoginInterceptor`，它每次请求都要去 Redis 里校验 Token 是否有效。Redis 一挂，拦截器直接抛出异常被全局异常处理器捕获，请求根本没进 Controller。
- **解决**：在拦截器里 catch `RedisConnectionFailureException`。如果 Redis 彻底挂了，降级为“仅校验 JWT 签名，不验主动登出”，放行请求。
- **面试话术**：“降级不能只考虑业务层。我的鉴权逻辑强依赖 Redis 校验 Token，如果 Redis 挂了，整个系统都会变成 500 死锁。所以我在拦截器层也做了容错，Redis 不可用时降级为无状态的 JWT 校验，虽然暂时失去了‘主动踢人下线’的能力，但保住了核心业务的可用性。”

**踩坑 3：Spring 6 的 TaskScheduler 调度报错**
- **现象**：在写 SSE 轮询推流时，调用 `taskScheduler.scheduleWithFixedDelay` 报编译错误，提示找不到合适的方法。
- **原因**：Spring 6（对应 Spring Boot 3）把这个方法的 4 参数重载（任务, 初始延迟, 延迟时间, 时间单位）删掉了，只剩下了 2 个参数。
- **解决**：改成传入 `Runnable` 和 `Duration` 对象（如 `Duration.ofMillis(800)`），并且必须要显式注入一个 `ThreadPoolTaskScheduler` 的 Bean，防止每次请求都新建线程导致 OOM。

**踩坑 4：Windows PowerShell 的 curl 别名导致 SSE 测试失败**
- **现象**：用终端测 SSE 接口时，报错 `Cannot bind parameter 'Headers'`。
- **原因**：在 Windows PowerShell 里，`curl` 是 `Invoke-WebRequest` 的内置别名，它不支持 Linux 下原生的 `-N`（禁用缓冲）参数。
- **解决**：必须使用 `curl.exe` 来执行原生的 cURL 命令，才能看到流式响应的效果。
- **面试话术**：“测试流式接口时，一定要用 `curl.exe -N` 禁用缓冲。用 Postman 或者 IDEA 的 HTTP Client 默认会等全部响应结束才显示，根本看不出流式的效果。”

**踩坑 5：小数据量下 MySQL 未命中索引**
- **现象**：对历史报告列表的 SQL 做 `EXPLAIN`，发现 `type: ALL`（全表扫描），没走 `idx_user_time`。
- **原因**：因为此时 `ai_report` 表里只有 9 行数据。MySQL 优化器评估后认为，走二级索引再回表查数据的成本，比直接全表扫描还要高。
- **解决与认知**：不需要刻意去加 `FORCE INDEX`。我清楚地知道，当数据量突破几千上万条后，优化器必然会走 `idx_user_time`。
- **面试话术**：“我在小数据量下测过 EXPLAIN，发现没走索引，这是 MySQL 优化器的成本决策。但我建立的 `(user_id, create_time)` 联合索引和 `ORDER BY create_time DESC, id DESC` 完全契合，一旦数据量上去，必然会触发 `Backward index scan` 反向扫描，避免 filesort 排序。”


## D-29 MySQL 为真值、Redis 为加速层；不一致时以 SQL 为准
问题：统计数字有两个来源，谁说了算。候选：① 只查 MySQL；② 只信 Redis；③ Redis 加速 + MySQL 兜底且为真值。选择 ③。放弃 ①：每次答题都聚合，数据量大后接口变慢；放弃 ②：Redis 重启/清空后统计直接错。验证：verify_stat.py 的 diff=0。局限：读路径不做自动纠错，只在对账脚本里暴露，靠人工 DEL 触发回填。

## D-30 回填只在 key 不存在时做，不在命中时纠正
问题：发现不一致时要不要顺手改。候选：① 每次读都校验并覆盖；② 只在 miss 时回填。选择 ②。放弃 ①：读与答题写并发，覆盖会把刚自增的一次抹掉，制造新错。代价：不一致要靠脚本发现。

## D-31 Redis 不可用时统计降级直查 MySQL，catch 粒度定在 DataAccessException
问题：缓存故障时接口该报错还是变慢。候选：① 500；② 返回全 0；③ 降级直查。选择 ③。放弃 ②：把“不知道”显示成“0”比报错更糟。放弃 catch Exception：会把 NPE 这类代码 bug 伪装成 Redis 故障。验证：验收停 Redis 后 200。

## D-32 今日计数用 HASH + Lua（HINCRBY + EXPIREAT），TTL 定在次日 02:00
问题：计数怎么存、什么时候过期。候选：① 两个 String key 分别 INCR；② HASH + 两条命令；③ HASH + Lua 原子。选择 ③。放弃 ①/②：EXPIRE 失败即永久 key。TTL 用 EXPIREAT 而非 EXPIRE：绝对时间不受执行延迟影响。

## D-33 连续打卡用 Bitmap + BITFIELD 整月读，streak 与 lastStreak 分开
问题：连续天数怎么算。候选：① 日期比对；② 每天一个 String key；③ Bitmap 每月一 key。选择 ③。放弃 ①：要遍历结果集；放弃 ②：读一次要 30 次往返。细节：offset=日-1，BITFIELD GET u31 一次取整月，位序按 MSB 计算。

## D-34 历史列表做真分页，只返回摘要；排序必须带 id 兜底
问题：03 写“最近 10 份摘要”又给了 page/size。候选：① 固定 10 条；② 真分页 + 摘要投影。选择 ②。实现：ORDER BY create_time DESC, id DESC；摘要用 JSON_EXTRACT + JSON_VALID 在库内算。局限：JSON 函数无法走索引，但只作用于当前用户的分页窗口。

## D-35 SSE 做分段回放，不做 token 级流式；鉴权走 Authorization 头
问题：简历写了“SSE 流式返回”，做成什么样。候选：① 真流式；② 分段回放；③ 不做。选择 ②。放弃 ①：流式文本无法解析成 ReportContent。放弃 ③：成本可控。鉴权：不加 ?token=，前端用 fetch + ReadableStream。

## D-36 统计不做预聚合表，写明触发条件
问题：什么时候算大。候选：① 现在就建 stat_daily 表；② 不建，写清触发条件。选择 ②。触发条件：单用户 >10 万条答题记录，或统计接口 P95 >200ms。验证：benchmark W5 段的三组数字。局限：真到阈值时改造要动答题链路。

## D-37 写库与统计埋点必须同属一个事务边界，错题重练也算「今日答题」
问题：W5 只给单题答题（AnswerService）埋了 Redis 统计，错题重练（WrongBookService.submitPractice）只写 user_answer_record，两条链路各算各的，对账必然漂。
候选：① 重练不算今日答题（统计只看单题接口）；② 重练也算，埋点统一放 afterCommit；③ 统计改成实时聚合 MySQL。
选择 ②。放弃 ①：用户眼里都是「今天做了题」，打卡按接口区分没有任何意义，而且 /stat/trend 读的是 MySQL，会和 /stat/today 自相矛盾。
放弃 ③：等于放弃 Redis 计数的意义，回到 D-29 已否掉的方案。
实现：两条链路都在 `TransactionSynchronizationManager.registerSynchronization` 的 afterCommit 里写 Redis，回滚时统计不动。
验证：PracticeStatConsistencyTest（练 1 题后 Redis t=1 c=0、打卡位置 1、MySQL 1 行），以及 verify_stat.py diff=0。

## D-38 重练结果字段口径：correct = 答对题数，skipped ⊆ correct
问题：重练时答对了但该题已不在错题本（被手动移除或同批重复提交），原来记 SKIPPED 且不计入 correct，导致 accuracy 偏低（5 题全对可能显示 0）。
候选：① 保持 skipped 独立、accuracy 只按 MASTERED 算；② correct 按答对数算，skipped 作为 correct 的子集。
选择 ②。total = correct + wrong，accuracy = correct / total，与单题接口算法一致；前端若要区分「已掌握」看 details[].status。
局限：skipped 的语义从「答对但未迁移」变成「其中已不在错题本的」，读接口时要按 D-38 解释。

## D-39 集成测试起 RANDOM_PORT 真实 Web 上下文，测试里关掉 MQ 监听
问题：W2 的事务实验用 `webEnvironment = NONE` 起不来——knife4j 的自动配置要 springdoc 的 `SpringDocConfigProperties`，而 springdoc 只在 Web 上下文创建该 bean，整类测试直接 ApplicationContext 加载失败（Tests run: 2, Errors: 2）。
候选：① 测试里排除 knife4j 自动配置；② 起 Web 上下文（RANDOM_PORT）；③ 不用 Spring 上下文，纯 JDBC 手写事务。
选择 ②。放弃 ①：要维护一长串 exclude，且掩盖「真实启动能不能跑」这件事；放弃 ③：测不到代理行为，等于没测事务失效。
配套：`spring.rabbitmq.listener.simple.auto-startup=false`（不依赖 broker）、占位 api-key（不依赖真实 AI key）。
代价：测试依赖本机 MySQL/Redis（3307/6379），不是纯离线单测，CI 里要带 compose。

## D-40 一条命令起四服务：健康检查 + mem_limit + .env
问题：W6 的 DoD 是「新机器 clone → compose up → doc.html 能打开」，原来 compose 只有三个中间件、没有限额也没有健康检查。
选择：四个服务都写 healthcheck；app 用 `depends_on: condition: service_healthy` 等三个中间件就绪；限额按 16GB 机器实测口径——mysql 768m（buffer pool 256M、关 performance_schema）、redis 256m（maxmemory 200mb + allkeys-lru）、rabbitmq 512m（内存高水位 0.4）、app 768m（-Xmx512m、MaxMetaspace 192m）。
理由：不设内存高水位等于没保护。注意 RabbitMQ 3.13 起 `RABBITMQ_VM_MEMORY_HIGH_WATERMARK` 环境变量已被判定为 deprecated，设置了会直接启动失败——必须走 `deploy/rabbitmq.conf` 的 `vm_memory_high_watermark.relative = 0.4`。同样，redis 的启动参数只能用字符串形式（`command: redis-server --appendonly yes --maxmemory 200mb ...`），写成 YAML 列表会被 entrypoint 当成配置文件解析而启动失败。
不给 app 设堆上限时 Spring Boot 3.5 按物理内存 25% 取堆，压测那周会吃掉整机内存。
密码与密钥走 deploy/.env（.gitignore 忽略），仓库里只留 .env.example。
验证：`docker compose up -d mysql redis rabbitmq` 后三容器 status=healthy、`docker inspect` 的 HostConfig.Memory 分别为 768/256/512MB、`CONFIG GET maxmemory`=209715200、`rabbitmq-diagnostics environment` 里 vm_memory_high_watermark=0.4。

## D-41 DB / Redis / MQ / JWT 全部走环境变量，密钥不进仓库
问题：W4 只外置了 AI key 和 DB/MQ 密码，JWT secret 还是写死的 32 字节字符串，泄露即可伪造任意用户 token。
选择：五类配置全部 `${VAR:默认值}`，容器里由 compose 注入，本地不设时用默认值保证能跑。
代价：默认值仍在代码里（本地开发便利），部署时必须改；README 与 .env.example 里都写了生成命令。
遗留：历史提交里仍有旧的明文 DeepSeek key（6d0c677 之前），已轮换，若要彻底清历史需 git filter-repo 重写。

## D-42 多阶段构建 + 非 root 运行，运行镜像只留 JRE 与 jar
问题：应用镜像怎么做。候选：① jib 插件；② buildpacks；③ 手写多阶段 Dockerfile。
选择 ③。放弃 ①：构建过程藏进插件，面试反而少一个可讲点；放弃 ②：基础镜像不可控、体积更大。
实现：构建阶段 `maven:3.9-eclipse-temurin-21`，先 COPY 两个 pom 再 `dependency:go-offline`，最后才 COPY src——改一行 Java 只重跑 package，不用重下 spring-ai 那批依赖；运行阶段 `eclipse-temurin:21-jre-jammy`，建非 root 用户 app（uid 999），`COPY --chown` 一次解决 jar 属主。
验证：`docker images` 684MB（磁盘 224MB = 基础镜像 462MB + app.jar 59.5MB）；`docker exec study-app id` → uid=999(app)；`docker exec study-app --format '{{.Config.User}}'` → app。
局限：`dependency:go-offline` 后加了 `|| true`，插件解析失败不会中断构建，真正的成败由 package 决定。
换的条件：接入 CI 且需要可重复构建与镜像签名时，改 jib 或 BuildKit cache mount。

## D-43 健康检查用 bash 的 /dev/tcp，运行镜像里不装 curl
问题：jetumurin JRE 镜像里没有 curl / wget，compose 的 healthcheck 怎么写。候选：① apt 装 curl；② 换 alpine 基底；③ bash 的 `/dev/tcp` 探端口。
选择 ③。放弃 ①：+10MB 且多一层；放弃 ②：musl 下部分库要回归。
实现：`test: ['CMD-SHELL', 'bash -c "exec 3<>/dev/tcp/127.0.0.1/8080"']`，配 `start_period: 60s`；依赖状态另开 `/actuator/health` 供人工看。
验证：`docker ps` 四容器 healthy；`/dev/tcp` 在 dash 下会报 `cannot create /dev/tcp`，必须显式 bash。
局限：探活只证明端口在听，不证明依赖可用；start_period 不能省，否则冷启动 15–25 秒会被判成 unhealthy，`depends_on: service_healthy` 的下游永远起不来。
换的条件：上 K8s 改用 httpGet 探针 + readiness/liveness 分级。

## D-44 初始化数据走 initdb + 种子 SQL，不引 Flyway / Liquibase
问题：空库起容器要自动建表灌 200 题。候选：① initdb；② Flyway；③ 手工脚本。
选择 ①。放弃 ②：零依赖、演示够用，一晚上能做完；放弃 ③：换机器就断。
实现：`deploy/sql/` 下五个 DDL 加 `06_seed_questions.sql`（由 `scripts/csv_to_sql.py` 从 CSV 生成，2 条 INSERT IGNORE）。
验证：`docker compose down -v` → `up -d` → 五张表 + question=200（Java 120 / MySQL 40 / Redis 40）+ ai_report=0。
局限：initdb 只在数据目录为空时执行一次，改了种子 SQL 必须 `down -v`；不解决表结构版本演进与回滚。
换的条件：多人协作或需要灰度/回滚时，必须换 Flyway。

## D-45 压测用自研 Python 脚本（标准库 http.client），不是 JMeter
问题：01 §7.2 写「JMeter 100 线程 × 30 秒」，但本机没装 JMeter，临时装一套要吃掉半天。候选：① 装 JMeter；② wrk；③ 自研脚本。
选择 ③。放弃 ①：装完还要写线程组与报告模板，收益只在工具名；放弃 ②：Windows 上没有原生包。
实现：`scripts/w6/bench_http.py`，threading + http.client 复用连接，记 n/avg/p50/p95/p99/max/fail/qps 与状态码分布。
验证：组 1 三接口 c=100 d=30s fail=0；组 2 六档梯度（50→300）全部 200。
局限：单机客户端与服务同机，c=300 时客户端自己也参与排队，QPS 只能算单实例下限，不能当集群容量。benchmark 里如实写「自研脚本 + 单机客户端」，不冒充 JMeter。
换的条件：要给团队出报告或做多机分布式压测时，上 JMeter + 独立压测机。

## D-46 缓存命中率主口径用 keyspace_misses，不用累计 hits，也不用 Com_select
问题：01 §7.2 要求 `CONFIG RESETSTAT` 后算 `hits/(hits+misses)`。但每次请求的 token 校验都会 GET 一次 Redis，全口径命中率必然接近 100%，这个数字不说明缓存生效。
候选：① 就用全口径；② 用 misses 作主口径；③ 用 MySQL Com_select 算回源。
选择 ②，① 作对照写出来，③ 只作旁证。
验证：n=1000 c=1 冷组 misses=2（命中率 99.80%）、热组 misses=0（100%）；RT 冷 1.0ms / 热 0.9ms，max 25.5/22.5ms。
旁证失效的原因：MySQL 上存在约每 3 秒一次的背景 SELECT（空闲 3 秒也会 +1，与请求数无关），热组实测「回源 3 次」是噪声，不是缓存失效——这一条必须写进 benchmark，否则数字会被误读。
局限：RESETSTAT 会清掉同一实例上其它统计（本地单实例无影响）；miss 里也可能含空值缓存与详情缓存的 key。
换的条件：多实例共享 Redis 时改为客户端埋点统计每个 key 前缀的命中，服务器侧差值不可用。

## D-47 索引实验在影子表 question_idx_test 上做，不动业务表
问题：01 §7.2 要求在业务表上加/删索引跑 EXPLAIN，但删索引会污染 W1–W5 的数据与演示现场。
选择：建影子表（W2 的做法），`CREATE TABLE question_idx_test LIKE question` 后删掉两个索引，灌 200×32=6400 行，三状态对比。
验证：计数 SQL 三状态 A 全表扫 6400 行 1.28ms / B 两列回表 1280 行 2.17ms / C 三列覆盖索引 448 行 0.10ms。
关键结论（如实写）：6400 行规模下两列索引比全表扫还慢，因为选择性只有 1280/6400，回表成本超过了顺序扫；三列索引才真正生效。
局限：影子表结构与真实表可能漂移，必须用 LIKE 复制；分页查询（ORDER BY id LIMIT 10）在任何状态下都可能走 PRIMARY，看不出差别，必须配 COUNT 版本才能暴露全扫成本。
换的条件：数据量到十万级且难度分布不均时重测，三列优势才稳定。

## D-48 容器内存限额与 -Xmx512m 配套，实测吞吐平台 3000–3600 QPS
问题：16GB 机器上四个容器各给多少内存，压测会不会把机器拖死。
选择：mysql 768m（buffer pool 256M、关 performance_schema）、redis 256m（maxmemory 200mb + allkeys-lru）、rabbitmq 512m（高水位 0.4）、app 768m（-Xmx512m、MaxMetaspace 192m）。
验证：`docker inspect` HostConfig.Memory = 768/256/512/768MB；组 1 三接口 c=100 全部 fail=0；组 2 六档：c=50 即 3645 QPS，之后 2950–3400 平台，P95 从 30ms 线性涨到 195ms，容器 CPU 峰值 473%（4.7/12 核）。
结论：吞吐在约 50 并发饱和，之后只是排队；CPU 未打满，说明瓶颈不是算力。
局限：512m 堆在 c=300 时 max 到 2.38s，长尾来自 GC。
换的条件：要跑 1000 并发，先把堆加到 1g 看 GC 日志，再考虑多实例，而不是只加限额。

## D-49 不做 Nginx / CI / Prometheus
问题：面试常问「有没有做反向代理、流水线、监控」，本项目要不要补。
选择：都不做。理由：演示链路是单实例直连，加一层 Nginx 只增加故障面；CI 与监控的面试价值可以用「actuator + traceId + 一次慢查询排查」讲清楚，不必真搭。
验证：README 已知未做项里写明；组 6 的五场景全靠 `rabbitmqctl` 与 `docker logs` 就能定位。
换的条件：多实例或要 HTTPS → 先补 Nginx；要发布流水线 → 再补 CI；要 SLO 与告警 → 最后补 Prometheus + Grafana，按这个顺序。

## D-50 Redis 故障时 today 路径本地短路 30 秒
问题：停 Redis 后 `/api/stat/today` 单次 8.09s（2s 超时 × 多次 Redis 读 + 回填重试），演示时停 Redis 会白屏。
候选：① 只把超时降到 500ms；② 只加短路；③ 两者都做。
选择 ③：降超时解决单次最坏延迟，短路解决「每条请求都去等一次超时」。
实现：`spring.data.redis.timeout=500ms`；`StatService` 用 volatile 时间戳记 30 秒故障窗口，窗口内不再碰 Redis（计数与打卡都跳过），回填也一并跳过。
验证：停 Redis 后实测首次 1.03s、后续 0.52s（改前 8.09s），接口始终 200，target <2s 达成。
局限：剩下的 0.5s 来自登录拦截器的 token 校验——那一路也有 500ms 超时 + 无状态 JWT 兜底；要再降得给拦截器也加短路，W6 不做。
换的条件：如果 stat 变成核心链路，应引入熔断器（Resilience4j）统一管理 Redis 故障，而不是各服务各自打时间戳。
## D-51 error_msg 统一裁剪到 240 字符（组 6 压测逼出来的真 bug）
问题：组 6 的 S2 场景（消费连续失败 3 次后写模板报告）实测一直写不进去，留下 status=0 的僵尸行、DLQ 也收不到审计消息。
根因：`ai_report.error_msg` 是 varchar(255)，而 fallback 里写的是 `"retry_exhausted: " + e.getMessage()`，MyBatis 的异常信息带整段 SQL 文本，轻松超过 255 → `Data too long for column 'error_msg'` → 模板报告这一步自己失败。
候选：① 把列加宽到 1024；② 在写入前裁剪；③ 只存异常类名。
选择 ②（`ReportConsumer.trimMsg()` 裁到 240）。放弃 ①：改表要 down -v 重建卷，演示现场会丢；放弃 ③：错误摘要太短，排查时看不出是哪条 SQL。
验证：修复后同一场景 status=2、model=template-v1、error_msg 以 `retry_exhausted` 开头且不再报错，DLQ 收到 1 条审计消息。
局限：裁剪会丢掉异常后半段，必要时仍需看 `docker logs` 的完整堆栈。
换的条件：如果以后要按错误类型做统计，应该单独开 `error_code` 列存枚举，而不是靠 error_msg 解析。
