# 渲染服务镜像
#
# 基础镜像用官方 Playwright 镜像：浏览器与其系统依赖已就绪，且浏览器构建与 npm 包版本严格对应，
# 避免「自己装 Chromium 后版本对不上、渲染结果不稳定」。
FROM mcr.microsoft.com/playwright:v1.49.1-jammy

# 中文字体：Debian/Ubuntu 的 fonts-noto-cjk 采用 SIL OFL 1.1 许可，
# 允许商用与再分发（保留许可声明即可，见 FONT-LICENSE.md）。
# 这里刻意不引入任何需要单独购买授权的商用字体——字体授权是业务侧决策，不由工程擅自替代。
RUN apt-get update \
 && apt-get install -y --no-install-recommends fonts-noto-cjk \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 先装依赖再拷代码，利用层缓存
COPY package.json ./
RUN npm install --omit=dev --no-audit --no-fund

COPY server.mjs ./
COPY smoke.mjs ./
COPY templates ./templates
COPY FONT-LICENSE.md ./

ENV PORT=8090 \
    VIEWPORT_WIDTH=750 \
    CJK_FAMILY="Noto Sans CJK SC" \
    NODE_ENV=production

EXPOSE 8090

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD node -e "fetch('http://127.0.0.1:'+(process.env.PORT||8090)+'/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"

CMD ["node", "server.mjs"]
