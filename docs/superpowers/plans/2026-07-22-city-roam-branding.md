# 城市漫游品牌化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (\`- [ ]\`) syntax for tracking.

**Goal:** 将本地生活服务项目重新包装为“城市漫游”，消除公开文件与源码中的原项目标识，并以不改变业务接口的方式提升移动端视觉质量。

**Architecture:** 所有 Java 源文件和测试从 \`com.hmdp\` 同步迁移至 \`com.cityroam\`，配置和 MyBatis XML 随之更新；Spring 组件扫描仍由新启动类覆盖整个基础包。前端静态目录改为 \`cityroam\` 并由 Nginx 指向该目录，HTML 中的接口与页面路由维持不变；公共 CSS 提供统一的颜色与组件覆盖，页面专属 CSS 只处理布局和内容卡片。

**Tech Stack:** Java 8、Spring Boot 2.3、MyBatis-Plus、Redis、Nginx、Vue 2、Element UI、CSS。

## Global Constraints

- 不修改 \`/shop\`、\`/blog\`、\`/voucher\`、\`/user\` 等既有 HTTP API 路径或字段。
- 不修改数据表名称、Redis key 或 SQL 业务内容。
- 数据库物理名称 \`hmdp\` 保留在 \`application.yaml\` 和 README 的连接/导入命令中，作为本地数据兼容例外；所有其他 \`hmdp\`、\`hmdianping\`、\`黑马点评\` 文本必须移除。
- 不新增前端框架、构建步骤或网络依赖。
- 每一任务完成后运行该任务指定的验证命令；仅在通过后创建提交。

---

## File structure

| 路径 | 责任 |
| --- | --- |
| \`src/main/java/com/cityroam/**\` | 城市漫游后端代码，保留原有分层和业务实现。 |
| \`src/test/java/com/cityroam/**\` | 更新包名和启动类引用后的测试。 |
| \`src/main/resources/application.yaml\` | 新应用名称、包扫描路径和日志路径；保留现有数据库连接。 |
| \`src/main/resources/mapper/VoucherMapper.xml\` | 与新 Java 包一致的 mapper namespace 和 result type。 |
| \`nginx-1.18.0/conf/nginx.conf\` | 以 \`html/cityroam\` 为静态资源根目录。 |
| \`nginx-1.18.0/html/cityroam/**\` | 城市漫游的页面、脚本、样式、图片与字体。 |
| \`README.md\` | 新项目名称、目录和启动说明。 |
| \`src/main/resources/db/cityroam.sql\` | 使用新文件名的既有建库数据脚本。 |

### Task 1: 迁移 Java 基础包与工程标识

**Files:**
- Move: \`src/main/java/com/hmdp/**\` → \`src/main/java/com/cityroam/**\`
- Move: \`src/test/java/com/hmdp/**\` → \`src/test/java/com/cityroam/**\`
- Rename: \`src/main/java/com/cityroam/HmDianPingApplication.java\` → \`src/main/java/com/cityroam/CityRoamApplication.java\`
- Rename: \`src/test/java/com/cityroam/HmDianPingApplicationTests.java\` → \`src/test/java/com/cityroam/CityRoamApplicationTests.java\`
- Modify: \`pom.xml\`, \`src/main/resources/application.yaml\`, \`src/main/resources/mapper/VoucherMapper.xml\`

**Interfaces:**
- Consumes: 现有 \`com.hmdp\` 的所有 Java 类型和 Mapper XML。
- Produces: 以 \`com.cityroam\` 为根包、功能和公开 HTTP 接口完全不变的可编译后端。

- [ ] **Step 1: 记录当前编译基线**

Run: \`mvn -DskipTests compile\`

Expected: Maven 返回 \`BUILD SUCCESS\`；若失败，停止迁移并记录基线失败原因，不能将其归因于改名。

- [ ] **Step 2: 移动目录并替换 Java 与 XML 的包引用**

Run:

~~~powershell
git mv src/main/java/com/hmdp src/main/java/com/cityroam
git mv src/test/java/com/hmdp src/test/java/com/cityroam
git mv src/main/java/com/cityroam/HmDianPingApplication.java src/main/java/com/cityroam/CityRoamApplication.java
git mv src/test/java/com/cityroam/HmDianPingApplicationTests.java src/test/java/com/cityroam/CityRoamApplicationTests.java
Get-ChildItem src/main/java,src/test/java,src/main/resources/mapper -Recurse -File -Include *.java,*.xml | ForEach-Object {
  (Get-Content -Raw $_.FullName).Replace('com.hmdp', 'com.cityroam').Replace('HmDianPingApplicationTests', 'CityRoamApplicationTests').Replace('HmDianPingApplication', 'CityRoamApplication') | Set-Content -NoNewline $_.FullName
}
~~~

The renamed application class must begin with:

~~~java
package com.cityroam;

@SpringBootApplication
public class CityRoamApplication {
    public static void main(String[] args) {
        SpringApplication.run(CityRoamApplication.class, args);
    }
}
~~~

- [ ] **Step 3: 更新构建坐标和 Spring/MyBatis 配置**

Make these exact replacements:

~~~xml
<groupId>com.cityroam</groupId>
<artifactId>city-roam</artifactId>
<name>city-roam</name>
<description>City Roam local discovery platform</description>
~~~

~~~yaml
spring:
  application:
    name: city-roam
mybatis-plus:
  type-aliases-package: com.cityroam.entity
logging:
  level:
    com.cityroam: debug
~~~

Keep the datasource URL database segment as \`3306/hmdp\`.

- [ ] **Step 4: Compile and run the renamed test suite**

Run: \`mvn test\`

Expected: compilation completes with no \`com.hmdp\` import or component-scan error. If the Redis-dependent \`CityRoamApplicationTests\` cannot connect to the configured Redis host, run \`mvn -DskipTests package\` and report that the environment dependency, rather than disabling the test.

- [ ] **Step 5: Commit the backend rename**

~~~powershell
git add pom.xml src/main/java src/test/java src/main/resources/application.yaml src/main/resources/mapper/VoucherMapper.xml
git commit -m "refactor: rename backend to city roam"
~~~

### Task 2: 迁移静态站点并移除旧品牌文本

**Files:**
- Move: \`nginx-1.18.0/html/hmdp/**\` → \`nginx-1.18.0/html/cityroam/**\`
- Modify: \`nginx-1.18.0/conf/nginx.conf\`
- Modify: \`nginx-1.18.0/html/cityroam/index.html\`, \`blog-edit.html\`, \`blog-detail.html\`, \`info-edit.html\`, \`info.html\`, \`login.html\`, \`login2.html\`, \`other-info.html\`, \`shop-detail.html\`, \`shop-list.html\`

**Interfaces:**
- Consumes: 既有 HTML、Vue 页面路由、\`/api\` 反向代理。
- Produces: 由同一 URL 和 API 提供、品牌显示为“城市漫游”的静态站点。

- [ ] **Step 1: Rename the static root and point Nginx to it**

~~~powershell
git mv nginx-1.18.0/html/hmdp nginx-1.18.0/html/cityroam
(Get-Content -Raw nginx-1.18.0/conf/nginx.conf).Replace('root   html/hmdp;', 'root   html/cityroam;') | Set-Content -NoNewline nginx-1.18.0/conf/nginx.conf
~~~

- [ ] **Step 2: Replace the visible brand text without altering routes**

In every listed HTML document, replace:

~~~html
<title>黑马点评</title>
~~~

with:

~~~html
<title>城市漫游 · 发现城市好去处</title>
~~~

In both login pages, replace \`《黑马点评用户服务协议》\` with \`《城市漫游用户服务协议》\`. In \`shop-detail.html\`, replace \`copyright ©2021 hmdp.com\` with \`© 2026 城市漫游 · 城市生活探索平台\`.

- [ ] **Step 3: Prove HTML assets and routes remain intact**

~~~powershell
rg -n 'location\.href|axios\.(get|post|put|delete)|src="\./css|src="\./js' nginx-1.18.0/html/cityroam
rg -n 'root\s+html/cityroam' nginx-1.18.0/conf/nginx.conf
~~~

Expected: existing navigation and Axios calls are still present; Nginx contains exactly one \`root   html/cityroam;\` directive.

- [ ] **Step 4: Commit the static-site migration**

~~~powershell
git add nginx-1.18.0/conf/nginx.conf nginx-1.18.0/html
git commit -m "refactor: move static site to city roam"
~~~

### Task 3: 建立城市漫游的统一视觉语言

**Files:**
- Modify: \`nginx-1.18.0/html/cityroam/css/main.css\`
- Modify: \`nginx-1.18.0/html/cityroam/css/index.css\`, \`login.css\`, \`shop-list.css\`, \`shop-detail.css\`, \`blog-edit.css\`, \`blog-detail.css\`, \`info.css\`
- Modify: \`nginx-1.18.0/html/cityroam/js/footer.js\`

**Interfaces:**
- Consumes: 既有 CSS class 名、Element UI 组件 class 和 \`footBar\` 的 \`activeBtn\` 属性。
- Produces: 不改变 Vue 数据绑定与 API 的现代化移动端页面外观。

- [ ] **Step 1: Add global visual tokens and safe base styles to \`main.css\`**

Append:

~~~css
:root {
    --roam-navy: #12355b;
    --roam-blue: #1d5d9b;
    --roam-orange: #ff8a3d;
    --roam-cream: #f6f8fb;
    --roam-text: #1d2a3a;
    --roam-muted: #718096;
    --roam-card-shadow: 0 10px 28px rgba(18, 53, 91, 0.10);
}

body {
    color: var(--roam-text);
    background: var(--roam-cream);
    font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
}

.foot {
    height: 64px;
    background: rgba(255, 255, 255, 0.96);
    box-shadow: 0 -8px 28px rgba(18, 53, 91, 0.08);
}

.active, .el-tabs__item.is-active {
    color: var(--roam-orange);
}
~~~

- [ ] **Step 2: Update the home feed and navigation appearance**

In \`index.css\`, make \`.search-bar\` use \`background: linear-gradient(135deg, var(--roam-navy), var(--roam-blue));\`; make \`.blog-list\` use \`background: var(--roam-cream);\`; make \`.blog-box\` use \`border-radius: 14px; box-shadow: var(--roam-card-shadow); overflow: hidden;\`; make \`.type-list\` use \`background: #fff; box-shadow: 0 4px 18px rgba(18, 53, 91, 0.08);\`; and change \`.blog-img img\` to \`border-radius: 0; display: block;\`.

In \`footer.js\`, preserve every \`location.href\` branch and change the unused map label from \`地图\` to \`漫游地图\`.

- [ ] **Step 3: Apply the same card and header treatment to the remaining pages**

Add these exact overrides to the end of the named stylesheet:

~~~css
/* shop-list.css */
.header { background: linear-gradient(135deg, var(--roam-navy), var(--roam-blue)); }
.shop-box { border-radius: 14px; box-shadow: var(--roam-card-shadow); margin: 10px 12px; }

/* shop-detail.css and blog-detail.css */
.header { background: linear-gradient(135deg, var(--roam-navy), var(--roam-blue)); }
.shop-info-box, .blog-info-box, .voucher-box, .comment-box { border-radius: 14px; box-shadow: var(--roam-card-shadow); }

/* blog-edit.css and info.css */
.header { background: linear-gradient(135deg, var(--roam-navy), var(--roam-blue)); }
.shop-dialog, .info-box, .edit-container { border-radius: 14px; box-shadow: var(--roam-card-shadow); }

/* login.css */
.login-container { background: linear-gradient(155deg, #eef5ff, #fff7f1); }
.header { background: linear-gradient(135deg, var(--roam-navy), var(--roam-blue)); }
.login-form { border-radius: 16px; box-shadow: var(--roam-card-shadow); background: #fff; padding: 20px; }
~~~

Do not rename existing selectors or alter fixed-height scrolling containers.

- [ ] **Step 4: Check that every page loads shared CSS and has the new title**

~~~powershell
rg -L '<link href="\./css/main.css" rel="stylesheet">' nginx-1.18.0/html/cityroam/*.html
rg -L '<title>城市漫游 · 发现城市好去处</title>' nginx-1.18.0/html/cityroam/*.html
~~~

Expected: both commands return no file paths.

- [ ] **Step 5: Commit the visual refresh**

~~~powershell
git add nginx-1.18.0/html/cityroam/css nginx-1.18.0/html/cityroam/js/footer.js
git commit -m "feat: refresh city roam mobile visual design"
~~~

### Task 4: 更新交付文档、脚本名并进行去标识化验收

**Files:**
- Rename: \`src/main/resources/db/hmdp.sql\` → \`src/main/resources/db/cityroam.sql\`
- Modify: \`README.md\`
- Modify: \`docs/superpowers/specs/2026-07-22-city-roam-branding-design.md\`

**Interfaces:**
- Consumes: 已迁移的包、启动类、前端目录和数据库脚本文件名。
- Produces: 可按 README 操作且没有不兼容旧品牌说明的交付物。

- [ ] **Step 1: Rename the database script and update user-facing documentation**

~~~powershell
git mv src/main/resources/db/hmdp.sql src/main/resources/db/cityroam.sql
~~~

Make these README replacements:

~~~text
# 城市漫游（City Roam）
html/cityroam/
main/java/com/cityroam/
CityRoamApplication.java
src/main/resources/db/cityroam.sql
mysql -u root -p hmdp < src/main/resources/db/cityroam.sql
~~~

Remove the repository clone URL and directory command that contain the old project name; replace them with \`git clone <your-repository-url>\` and \`cd city-roam\`.

- [ ] **Step 2: Run source and public-file identifier audit**

~~~powershell
rg -n -i --glob '!target/**' --glob '!nginx-1.18.0/logs/**' --glob '!docs/superpowers/**' 'hmdianping|黑马点评' .
rg -n -i --glob '!target/**' --glob '!nginx-1.18.0/logs/**' --glob '!docs/superpowers/**' 'hmdp' .
~~~

Expected: the first command has no results. The second command returns only the approved datasource database name and README database import/connection instructions. Any Java, HTML, Nginx-root, Maven coordinate, file-path or class-name occurrence is a failure to fix before continuing.

- [ ] **Step 3: Run final package verification**

Run: \`mvn -DskipTests package\`

Expected: \`BUILD SUCCESS\` and \`target/city-roam-0.0.1-SNAPSHOT.jar\` exists.

- [ ] **Step 4: Commit final documentation and validation-ready rename**

~~~powershell
git add README.md src/main/resources/db/cityroam.sql docs/superpowers/specs/2026-07-22-city-roam-branding-design.md
git commit -m "docs: document city roam setup"
~~~

## Plan self-review

- Spec coverage: Tasks 1 and 4 cover Java/config/build/docs/script renaming; Task 2 covers static-root migration and visible branding; Task 3 covers the agreed visual system; Task 4 audits source and public files and verifies packaging.
- Placeholder scan: no unfinished markers or unspecified commands remain.
- Interface consistency: all tasks preserve the same Vue routes and HTTP APIs; new Java root package is consistently \`com.cityroam\`; only the database name is an explicit compatibility exception.
