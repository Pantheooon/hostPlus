## HostPlus

只支持http映射,https只支持转发

对比host文件外,支持一下三种映射(都可以带端口):
- ip到ip
- ip到域名
- 域名到域名

使用:
- chrome 里面下载`SwitchyOmega`,启动后添加代理端口和服务
![img.png](img.png)
- java代码如果有需要代理的,在启动项里面配置`-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=18080`
