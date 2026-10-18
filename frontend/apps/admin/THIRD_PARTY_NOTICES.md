# 第三方组件记录

本应用的上游代码来源和许可证见 `UPSTREAM.md` 与 `LICENSE`。直接运行依赖固定在 `package.json` 和 `pnpm-lock.yaml`：

| 组件 | 版本 | 许可证 | 来源 |
|---|---:|---|---|
| Vue | 3.5.34 | MIT | https://github.com/vuejs/core |
| Vue Router | 5.0.6 | MIT | https://github.com/vuejs/router |
| Pinia | 3.0.4 | MIT | https://github.com/vuejs/pinia |
| Axios | 1.16.0 | MIT | https://github.com/axios/axios |
| Element Plus | 2.13.7 | MIT | https://github.com/element-plus/element-plus |
| Element Plus Icons | 2.3.2 | MIT | https://github.com/element-plus/element-plus-icons |

构建与测试依赖同样由锁文件固定。发布前应对最终锁文件生成机器可读 SBOM 并执行许可证扫描；本记录不替代传递依赖扫描。

| 构建/测试组件 | 版本 | 许可证 |
|---|---:|---|
| Vite | 8.1.4 | MIT |
| Vitest | 5.0.1 | MIT |
| TypeScript | 6.0.3 | Apache-2.0 |
| Vue TypeScript compiler | 3.2.8 | MIT |
| Vite Vue plugin | 6.0.6 | MIT |
| Node.js type declarations | 25.6.0 | MIT |
