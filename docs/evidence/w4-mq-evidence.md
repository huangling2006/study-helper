演示了成功 status=1、伪造重投验证幂等、并发触发限流 429
2026-11-04 19:50:25 [rabbit-simple-0] INFO  c.h.s.report.consumer.ReportConsumer - [] 收到报告生成消息: requestId=8e0a95ee051541318707f124e408be40
2026-11-04 19:50:27 [rabbit-simple-0] INFO  c.h.s.report.consumer.ReportConsumer - [] 报告生成完毕，已 ACK: requestId=8e0a95ee051541318707f124e408be40, status=1
2026-11-04 19:56:29 [rabbit-simple-0] INFO  c.h.s.report.consumer.ReportConsumer - [] 收到报告生成消息: requestId=26cd6e4d8b4140adb98a2d910750a474
2026-11-04 19:56:32 [rabbit-simple-0] INFO  c.h.s.report.consumer.ReportConsumer - [] 报告生成完毕，已 ACK: requestId=26cd6e4d8b4140adb98a2d910750a474, status=1
2026-11-04 19:56:41 [tomcat-handler-5] WARN  c.h.s.common.GlobalExceptionHandler - [3e3b3efd62b34cd3] business error, code=429, msg=操作过于频繁，请稍后再试
2026-11-04 19:56:42 [tomcat-handler-6] WARN  c.h.s.common.GlobalExceptionHandler - [69f3c437e9554334] business error, code=429, msg=操作过于频繁，请稍后再试
2026-11-04 19:56:43 [tomcat-handler-7] WARN  c.h.s.common.GlobalExceptionHandler - [527666fdceaa4810] business error, code=429, msg=操作过于频繁，请稍后再试
2026-11-04 19:56:43 [tomcat-handler-8] WARN  c.h.s.common.GlobalExceptionHandler - [766c93ea798d446d] business error, code=429, msg=操作过于频繁，请稍后再试
2026-11-04 19:56:44 [tomcat-handler-9] WARN  c.h.s.common.GlobalExceptionHandler - [244c19b37db9430b] business error, code=429, msg=操作过于频繁，请稍后再试
2026-11-04 20:01:15 [rabbit-simple-0] INFO  c.h.s.report.consumer.ReportConsumer - [] 收到报告生成消息: requestId=26cd6e4d8b4140adb98a2d910750a474