/**
 * 论坛 mock 聚合出口：http.ts 只认这里导出的 mockAdapter
 * 新增业务 mock 文件后，在下方 import 并参与 sideEffect 数组即可（顺序即注册顺序）
 */
import { mockAdapter } from './registry'
import './forum'
import './forum-admin'
import './tech'
import './me'

// 引用即注册：import 语句的副作用就是各业务处理器排队登记
export { mockAdapter }
