本module为smbj的源码copy

copy源码的目的在于，smbj默认情况下如果服务端表示需要账号登录，则本地直接拒绝guest、anonymous向外发出连接请求，而其实是可以尝试连接的

copy源码之后更改记录如下：

1. com.hierynomus.smbj.connection.SMBSessionBuilder.validateAndSetSigning()方法内connectionSigningRequired的值判断，写死为false
2. 删除不影响任何逻辑的冗余代码
3. 删除对org.slf4j日志库的依赖，改为使用空的slf4j实现



### 注意，如果对smbj有逻辑性更改，必须写在此处，否则后续smbj升级或者其他情况，会造成无法维护！
