# 城市漫游前端轻旅行体验升级 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将全部 CityRoam 静态移动端页面升级为轻旅行/生活方式视觉，并为已有动作补足可见交互反馈，不改变任何后端契约。

**Architecture:** 保留现有 Vue 2 页面内联实例、Axios 请求和跳转路径。新增一个跨页设计样式表与一个无依赖交互工具；页面仅增加语义化类、SVG 图标钩子和局部 `pending` 状态，不引入构建工具或网络运行时依赖。

**Tech Stack:** HTML、CSS、原生 JavaScript、Vue 2、Element UI、Axios、Nginx 静态文件服务、PowerShell、Maven。

## Global Constraints

- 只能修改 `nginx-1.18.0/html/cityroam` 下的前端资源，以及本计划指定的前端验证脚本。
- 不修改 API 路径、请求体、响应字段、Java 代码、数据库、Nginx 配置或 `application.yaml`。
- 所有图标使用下载到 `imgs/icons/` 的 Lucide SVG；不在页面中加载外部图标 CDN。
- 地图和消息入口调用 `CityRoamUI.comingSoon()`，不跳转到不存在的页面。
- 页面在 390px 视口优先呈现，在宽视口保持居中；交互动效必须遵守 `prefers-reduced-motion`。
- 每个任务完成后运行其指定检查并独立提交；不要暂存日志、`application.yaml`、崩溃日志或 `graphify-out/`。

---

### Task 1: 建立本地 SVG、共享视觉令牌和可验证的交互工具

**Files:**
- Create: `nginx-1.18.0/html/cityroam/imgs/icons/{LICENSE-lucide.txt,house.svg,map.svg,plus.svg,messages-square.svg,user-round.svg,search.svg,map-pin.svg,heart.svg,chevron-left.svg,x.svg,send.svg,sparkles.svg,pencil.svg,ticket.svg,star.svg,camera.svg,log-out.svg}`
- Create: `nginx-1.18.0/html/cityroam/css/travel-ui.css`
- Create: `nginx-1.18.0/html/cityroam/js/ui.js`
- Create: `tests/frontend/cityroam-ui-contract.ps1`

**Interfaces:**
- Produces `window.CityRoamUI.comingSoon(feature)`, `window.CityRoamUI.lockAction(vm, key, action)` and delegated image fallback behaviour.
- Produces reusable CSS classes: `.travel-page`, `.travel-card`, `.travel-button`, `.travel-button--secondary`, `.travel-icon`, `.travel-icon--*`, `.is-loading`, `.empty-state` and `.skeleton`.

- [ ] **Step 1: Write the failing static contract test.**

Create `tests/frontend/cityroam-ui-contract.ps1` with this check; it must fail before the shared assets and page links are added:

```powershell
$ErrorActionPreference = 'Stop'
$root = Join-Path $PSScriptRoot '..\..\nginx-1.18.0\html\cityroam'
$pages = Get-ChildItem -Path $root -Filter '*.html'
$requiredIcons = 'house','map','plus','messages-square','user-round','search','map-pin','heart','chevron-left','x','send','sparkles','pencil','ticket','star','camera','log-out'

foreach ($page in $pages) {
  $html = Get-Content -Raw $page.FullName
  if ($html -notmatch 'css/travel-ui\.css') { throw "$($page.Name) does not load travel-ui.css" }
  if ($html -notmatch 'js/ui\.js') { throw "$($page.Name) does not load ui.js" }
  if ($html -match 'https?://[^"'']*(lucide|heroicons|iconify)') { throw "$($page.Name) loads a remote icon" }
}

foreach ($icon in $requiredIcons) {
  if (-not (Test-Path (Join-Path $root "imgs/icons/$icon.svg"))) { throw "Missing local icon: $icon.svg" }
}

$footer = Get-Content -Raw (Join-Path $root 'js/footer.js')
if ($footer -notmatch 'CityRoamUI\.comingSoon') { throw 'Footer does not provide coming-soon actions' }
Write-Output 'CityRoam frontend shared UI contract passed.'
```

- [ ] **Step 2: Run the contract test and confirm it fails.**

Run: `powershell -ExecutionPolicy Bypass -File tests/frontend/cityroam-ui-contract.ps1`

Expected: failure naming a missing `travel-ui.css` reference or missing local icon.

- [ ] **Step 3: Download and record local Lucide assets.**

Run the following command to download exactly the named SVG files and the ISC license from the official Lucide repository. Keep the SVG source unmodified; do not add the Lucide package, CDN script or unrelated icon assets.

```powershell
$iconRoot = 'nginx-1.18.0/html/cityroam/imgs/icons'
$iconNames = 'house','map','plus','messages-square','user-round','search','map-pin','heart','chevron-left','x','send','sparkles','pencil','ticket','star','camera','log-out'
New-Item -ItemType Directory -Force -Path $iconRoot | Out-Null
foreach ($iconName in $iconNames) {
  Invoke-WebRequest -Uri "https://raw.githubusercontent.com/lucide-icons/lucide/main/icons/$iconName.svg" -OutFile (Join-Path $iconRoot "$iconName.svg")
}
Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/lucide-icons/lucide/main/LICENSE' -OutFile (Join-Path $iconRoot 'LICENSE-lucide.txt')
```

- [ ] **Step 4: Implement the shared UI contract.**

Create `js/ui.js` with this public API and failure-safe image handler:

```javascript
(function (window, document) {
  function notify(message, type) {
    if (window.ELEMENT && window.ELEMENT.Message) {
      window.ELEMENT.Message({ message: message, type: type || 'info', duration: 1800 });
      return;
    }
    window.alert(message);
  }

  function release(vm, key) {
    vm.$set ? vm.$set(vm, key, false) : vm[key] = false;
  }

  window.CityRoamUI = {
    comingSoon: function (feature) {
      notify((feature || '该功能') + '即将上线，敬请期待', 'info');
    },
    lockAction: function (vm, key, action) {
      if (vm[key]) return Promise.resolve();
      vm.$set ? vm.$set(vm, key, true) : vm[key] = true;
      return Promise.resolve().then(action).then(function (result) {
        release(vm, key);
        return result;
      }, function (error) {
        release(vm, key);
        throw error;
      });
    }
  };

  document.addEventListener('error', function (event) {
    var image = event.target;
    if (!image || image.tagName !== 'IMG' || image.dataset.travelFallback) return;
    image.dataset.travelFallback = 'true';
    image.removeAttribute('src');
    image.classList.add('image-fallback');
    image.alt = image.alt || '图片暂不可用';
  }, true);
})(window, document);
```

Create `travel-ui.css` with `:root` travel tokens, centered `.travel-page`, reusable card/button/loading/empty-state rules and CSS-mask icon mappings. Use this mapping pattern for every selected icon so icon colour follows `currentColor`:

```css
:root { --travel-ink: #143042; --travel-blue: #1d7f8c; --travel-coral: #f06f52; --travel-canvas: #f8f7f2; --travel-line: #e8e5dc; --travel-radius: 20px; }
.travel-icon { display:inline-block; width:1.2em; height:1.2em; vertical-align:-0.18em; background:currentColor; -webkit-mask:var(--travel-icon) center/contain no-repeat; mask:var(--travel-icon) center/contain no-repeat; }
.travel-icon--home { --travel-icon:url('/imgs/icons/house.svg'); }
.travel-icon--map { --travel-icon:url('/imgs/icons/map.svg'); }
.travel-icon--publish { --travel-icon:url('/imgs/icons/plus.svg'); }
.travel-icon--message { --travel-icon:url('/imgs/icons/messages-square.svg'); }
.travel-icon--profile { --travel-icon:url('/imgs/icons/user-round.svg'); }
.travel-icon--back { --travel-icon:url('/imgs/icons/chevron-left.svg'); }
.travel-icon--search { --travel-icon:url('/imgs/icons/search.svg'); }
.travel-icon--pin { --travel-icon:url('/imgs/icons/map-pin.svg'); }
.travel-icon--heart { --travel-icon:url('/imgs/icons/heart.svg'); }
.travel-icon--close { --travel-icon:url('/imgs/icons/x.svg'); }
.travel-icon--send { --travel-icon:url('/imgs/icons/send.svg'); }
.travel-icon--sparkles { --travel-icon:url('/imgs/icons/sparkles.svg'); }
.travel-icon--edit { --travel-icon:url('/imgs/icons/pencil.svg'); }
.travel-icon--ticket { --travel-icon:url('/imgs/icons/ticket.svg'); }
.travel-icon--star { --travel-icon:url('/imgs/icons/star.svg'); }
.travel-icon--camera { --travel-icon:url('/imgs/icons/camera.svg'); }
.travel-icon--logout { --travel-icon:url('/imgs/icons/log-out.svg'); }
@media (prefers-reduced-motion: reduce) { *, *::before, *::after { scroll-behavior:auto !important; transition-duration:0.01ms !important; animation-duration:0.01ms !important; } }
```

- [ ] **Step 5: Link the two shared assets on every page.**

Add `<link href="./css/travel-ui.css" rel="stylesheet">` after each page-specific stylesheet and add `<script src="./js/ui.js"></script>` after `common.js` on all ten HTML pages. Keep `footer.js` after `ui.js` where it is used.

- [ ] **Step 6: Run the contract test and Maven regression suite.**

Run: `powershell -ExecutionPolicy Bypass -File tests/frontend/cityroam-ui-contract.ps1; mvn test`

Expected: `CityRoam frontend shared UI contract passed.` and Maven reports `BUILD SUCCESS`.

- [ ] **Step 7: Commit the foundation.**

```powershell
git add nginx-1.18.0/html/cityroam/imgs/icons nginx-1.18.0/html/cityroam/css/travel-ui.css nginx-1.18.0/html/cityroam/js/ui.js tests/frontend/cityroam-ui-contract.ps1 nginx-1.18.0/html/cityroam/*.html
git commit -m "feat: add cityroam travel UI foundation"
```

### Task 2: 更新通用导航与首页的发现体验

**Files:**
- Modify: `nginx-1.18.0/html/cityroam/js/footer.js`
- Modify: `nginx-1.18.0/html/cityroam/index.html`
- Modify: `nginx-1.18.0/html/cityroam/css/main.css`
- Modify: `nginx-1.18.0/html/cityroam/css/index.css`

**Interfaces:**
- Consumes `CityRoamUI.comingSoon()` and the CSS icon classes from Task 1.
- Produces a semantic `footBar` whose map and message destinations are explicitly unavailable, while home/publish/profile retain their existing URLs.

- [ ] **Step 1: Extend the contract test for the footer behaviour.**

Append these assertions to `tests/frontend/cityroam-ui-contract.ps1`:

```powershell
if ($footer -notmatch "comingSoon\('漫游地图'\)") { throw 'Map navigation is not a coming-soon action' }
if ($footer -notmatch "comingSoon\('消息中心'\)") { throw 'Message navigation is not a coming-soon action' }
if ($footer -notmatch 'location\.href = "/blog-edit\.html"') { throw 'Publish navigation changed' }
```

- [ ] **Step 2: Run the contract test and confirm it fails.**

Run: `powershell -ExecutionPolicy Bypass -File tests/frontend/cityroam-ui-contract.ps1`

Expected: `Map navigation is not a coming-soon action`.

- [ ] **Step 3: Implement the navigation and discovery layout.**

In `footer.js`, replace clickable `div` navigation items with `button type="button" class="foot-box"` elements, include the `.travel-icon--home/map/publish/message/profile` spans and `aria-label`s, and implement the unavailable destinations exactly as:

```javascript
} else if (i === 2) {
  CityRoamUI.comingSoon('漫游地图');
} else if (i === 3) {
  CityRoamUI.comingSoon('消息中心');
}
```

In `index.html`, add `class="travel-page travel-page--home"` to the root, make the profile and AI controls real buttons, add travel icon spans to visible actions, and add `likePending: {}` to `data()`. Change `addLike(b)` to lock the existing API call by id, while preserving its refresh request:

```javascript
addLike(b) {
  if (this.likePending[b.id]) return;
  this.$set(this.likePending, b.id, true);
  axios.put('/blog/like/' + b.id)
    .then(() => this.queryBlogById(b))
    .catch(err => this.$message.error(err))
    .finally(() => this.$set(this.likePending, b.id, false));
}
```

Bind the button with `:disabled="likePending[b.id]"` and `:class="{ 'is-loading': likePending[b.id] }"`. Do not change the AI streaming request, conversation id or geolocation logic.

In `main.css` and `index.css`, move base colours, body/container geometry, header, bottom bar, category cards, blog cards and AI modal presentation to the travel tokens. Preserve all existing class names used by Vue templates.

- [ ] **Step 4: Run static and browser checks.**

Run: `powershell -ExecutionPolicy Bypass -File tests/frontend/cityroam-ui-contract.ps1`

Then start the existing Nginx configuration and verify at 390px: categories and blogs are readable, AI opens/closes, the like button disables during its request, and map/message show the agreed message without navigation.

- [ ] **Step 5: Commit the navigation and home increment.**

```powershell
git add nginx-1.18.0/html/cityroam/js/footer.js nginx-1.18.0/html/cityroam/index.html nginx-1.18.0/html/cityroam/css/main.css nginx-1.18.0/html/cityroam/css/index.css tests/frontend/cityroam-ui-contract.ps1
git commit -m "feat: refresh cityroam discovery experience"
```

### Task 3: 改造店铺列表与详情的旅行卡片体验

**Files:**
- Modify: `nginx-1.18.0/html/cityroam/shop-list.html`
- Modify: `nginx-1.18.0/html/cityroam/shop-detail.html`
- Modify: `nginx-1.18.0/html/cityroam/css/shop-list.css`
- Modify: `nginx-1.18.0/html/cityroam/css/shop-detail.css`

**Interfaces:**
- Consumes `CityRoamUI.lockAction(vm, key, action)`.
- Produces `shopLoading`, `voucherLoading` and a single `seckillPending` boolean only; all existing endpoint signatures remain unchanged.

- [ ] **Step 1: Write failing markup assertions.**

Append this page-level contract to the PowerShell test:

```powershell
$shopList = Get-Content -Raw (Join-Path $root 'shop-list.html')
$shopDetail = Get-Content -Raw (Join-Path $root 'shop-detail.html')
if ($shopList -notmatch 'sort-item.*active-sort') { throw 'Shop sort controls do not expose active state' }
if ($shopDetail -notmatch 'seckillPending') { throw 'Voucher purchase has no pending state' }
if ($shopDetail -notmatch 'travel-icon--ticket') { throw 'Voucher card does not use the local ticket icon' }
```

- [ ] **Step 2: Run the test and confirm it fails.**

Run: `powershell -ExecutionPolicy Bypass -File tests/frontend/cityroam-ui-contract.ps1`

Expected: `Shop sort controls do not expose active state`.

- [ ] **Step 3: Implement list filters and detail actions without changing requests.**

In `shop-list.html`, initialise `sortBy: ''` and `shopLoading: false`; set `sortBy` in `sortAndQuery(sortBy)`, bind `:class="{ 'active-sort': sortBy === 'comments' }"` (and the analogous distance/score controls), and set `shopLoading` around the existing `/shop/of/type` Axios request. Keep pagination parameters and `onScroll` unchanged. Mark shop cards as travel cards and add star/pin icons beside score/distance.

In `shop-detail.html`, initialise `seckillPending: false`. Bind the existing voucher action with `:disabled="isNotBegin(v) || v.stock < 1 || seckillPending"` and replace its method body with a lock around the same request:

```javascript
seckill(v) {
  if (this.isNotBegin(v) || v.stock < 1 || this.seckillPending) return;
  CityRoamUI.lockAction(this, 'seckillPending', () => axios.post('/voucher-order/seckill/' + v.id))
    .then(() => this.$message.success('抢购成功，订单已生成'))
    .catch(err => this.$message.error(err));
}
```

Restyle detail imagery, address, business information, voucher cards and disabled states in `shop-detail.css`.

- [ ] **Step 4: Run static, Maven and browser checks.**

Run: `powershell -ExecutionPolicy Bypass -File tests/frontend/cityroam-ui-contract.ps1; mvn test`

At 390px, confirm selected sort control changes appearance, cards remain tappable, unavailable vouchers are disabled, and an active voucher cannot submit twice.

- [ ] **Step 5: Commit the shop increment.**

```powershell
git add nginx-1.18.0/html/cityroam/shop-list.html nginx-1.18.0/html/cityroam/shop-detail.html nginx-1.18.0/html/cityroam/css/shop-list.css nginx-1.18.0/html/cityroam/css/shop-detail.css tests/frontend/cityroam-ui-contract.ps1
git commit -m "feat: refresh cityroam shop journeys"
```

### Task 4: 完善笔记阅读与发布交互

**Files:**
- Modify: `nginx-1.18.0/html/cityroam/blog-detail.html`
- Modify: `nginx-1.18.0/html/cityroam/blog-edit.html`
- Modify: `nginx-1.18.0/html/cityroam/css/blog-detail.css`
- Modify: `nginx-1.18.0/html/cityroam/css/blog-edit.css`

**Interfaces:**
- Produces booleans `likePending`, `followPending`, `uploadPending`, `deletePending` and `submitPending` scoped to the existing Vue instances.
- Uses existing `/blog`, `/blog/like/:id`, `/follow/:id/:followed`, `/upload/blog`, `/upload/blog/delete` and `/api/ai/review/stream` calls unchanged.

- [ ] **Step 1: Add failing interaction contract assertions.**

```powershell
$blogDetail = Get-Content -Raw (Join-Path $root 'blog-detail.html')
$blogEdit = Get-Content -Raw (Join-Path $root 'blog-edit.html')
if ($blogDetail -notmatch 'likePending|followPending') { throw 'Blog detail actions have no pending state' }
if ($blogEdit -notmatch 'uploadPending|submitPending') { throw 'Blog editor actions have no pending state' }
if ($blogEdit -notmatch 'travel-icon--sparkles') { throw 'AI review action does not use the local icon' }
```

- [ ] **Step 2: Run the contract test and confirm it fails.**

Run: `powershell -ExecutionPolicy Bypass -File tests/frontend/cityroam-ui-contract.ps1`

Expected: `Blog detail actions have no pending state`.

- [ ] **Step 3: Implement button locks and reading hierarchy.**

For `blog-detail.html`, add `likePending: false` and `followPending: false`, bind the like/follow buttons to disabled and `.is-loading` state, and wrap their existing Axios requests with `CityRoamUI.lockAction`. Preserve the existing refetch of blog and likes after a successful like. Replace visible Element icons with travel heart, back, profile and pin icons; update `blog-detail.css` to use an editorial reading column, compact author card, image carousel framing, travel shop card and fixed interaction bar.

For `blog-edit.html`, add `uploadPending`, `deletePending`, and `submitPending` data fields. Use `CityRoamUI.lockAction` for the existing upload, delete and `/blog` submit calls. Disable the corresponding control while its request is active and leave the AI review stream governed by its existing `aiGenerating` state. Make the upload surface, shop-picker sheet, AI review button and publish CTA use travel classes and local camera/search/sparkles/send icons.

- [ ] **Step 4: Run contract and browser checks.**

Run: `powershell -ExecutionPolicy Bypass -File tests/frontend/cityroam-ui-contract.ps1`

At 390px, verify back navigation, the detail carousel, like/follow locks, image selection/deletion, shop selection, AI review disabled state and successful publish navigation. Confirm an image with an invalid URL becomes a stable local fallback block.

- [ ] **Step 5: Commit the blog increment.**

```powershell
git add nginx-1.18.0/html/cityroam/blog-detail.html nginx-1.18.0/html/cityroam/blog-edit.html nginx-1.18.0/html/cityroam/css/blog-detail.css nginx-1.18.0/html/cityroam/css/blog-edit.css tests/frontend/cityroam-ui-contract.ps1
git commit -m "feat: improve cityroam note interactions"
```

### Task 5: 统一个人中心、资料与登录体验

**Files:**
- Modify: `nginx-1.18.0/html/cityroam/info.html`
- Modify: `nginx-1.18.0/html/cityroam/other-info.html`
- Modify: `nginx-1.18.0/html/cityroam/info-edit.html`
- Modify: `nginx-1.18.0/html/cityroam/login.html`
- Modify: `nginx-1.18.0/html/cityroam/login2.html`
- Modify: `nginx-1.18.0/html/cityroam/css/info.css`
- Modify: `nginx-1.18.0/html/cityroam/css/login.css`

**Interfaces:**
- Produces `savePending` in profile edit and `loginPending`/`codePending` in the supported login flow.
- Retains `/user/me`, `/user/:id`, `/user/info/:id`, `/user/info`, `/user/code`, `/user/login`, `/follow/:id/:followed` and session token behaviour.

- [ ] **Step 1: Add failing profile/login contract assertions.**

```powershell
$infoEdit = Get-Content -Raw (Join-Path $root 'info-edit.html')
$login = Get-Content -Raw (Join-Path $root 'login.html')
$login2 = Get-Content -Raw (Join-Path $root 'login2.html')
if ($infoEdit -notmatch 'savePending') { throw 'Profile save has no pending state' }
if ($login -notmatch 'codePending|loginPending') { throw 'Phone login has no pending state' }
if ($login2 -notmatch 'loginPending') { throw 'Password login has no pending state' }
```

- [ ] **Step 2: Run the contract test and confirm it fails.**

Run: `powershell -ExecutionPolicy Bypass -File tests/frontend/cityroam-ui-contract.ps1`

Expected: `Profile save has no pending state`.

- [ ] **Step 3: Implement the account and authentication UX.**

In `info.html`, `other-info.html` and `info-edit.html`, replace visible back/edit/logout/follow affordances with semantic buttons and travel icons. Restyle the profile header, statistics, tab content and note grid using `info.css`; keep all queries and navigation functions intact. Add `savePending: false` and wrap the existing profile save request in `CityRoamUI.lockAction`, binding the save CTA to `:disabled="savePending"`.

In `login.html`, add `codePending: false` and `loginPending: false`. Keep phone validation and the existing countdown, but lock `/user/code` only until the request resolves and lock `/user/login` until the response resolves. Bind the send-code and login buttons to their pending flags. In `login2.html`, add only `loginPending`, lock its existing `/user/login` call, and do not create a password-recovery API. Restyle `login.css` as a calm travel welcome panel while keeping the agreement checkbox and all current fields.

- [ ] **Step 4: Run static, Maven and browser checks.**

Run: `powershell -ExecutionPolicy Bypass -File tests/frontend/cityroam-ui-contract.ps1; mvn test`

At 390px, verify profile navigation, other-user follow feedback, save-button lock, agreement validation, send-code countdown, both login forms' loading state and the existing successful redirects.

- [ ] **Step 5: Commit the account increment.**

```powershell
git add nginx-1.18.0/html/cityroam/info.html nginx-1.18.0/html/cityroam/other-info.html nginx-1.18.0/html/cityroam/info-edit.html nginx-1.18.0/html/cityroam/login.html nginx-1.18.0/html/cityroam/login2.html nginx-1.18.0/html/cityroam/css/info.css nginx-1.18.0/html/cityroam/css/login.css tests/frontend/cityroam-ui-contract.ps1
git commit -m "feat: refresh cityroam account experience"
```

### Task 6: 完成视觉回归、可访问性检查与交付验证

**Files:**
- Modify if necessary: only the files named in Tasks 1-5, limited to defects found during validation.
- Test: `tests/frontend/cityroam-ui-contract.ps1`

**Interfaces:**
- Verifies the final UI consumes only local shared assets and preserves existing backend-facing URLs.

- [ ] **Step 1: Run the complete static and backend regression suite.**

Run: `powershell -ExecutionPolicy Bypass -File tests/frontend/cityroam-ui-contract.ps1; mvn test`

Expected: frontend contract passes and Maven reports `BUILD SUCCESS`.

- [ ] **Step 2: Run browser visual and interaction regression.**

With the existing Nginx service running, inspect every HTML page at 390px and a desktop width. Check: no horizontal overflow; no missing local SVGs; all buttons have a visible focus state; no uncontrolled double-submit; image fallback preserves card geometry; and all changed console output is error-free. Exercise the existing API-backed paths only where local test data permits; record unavailable backend responses as environment limitations, not UI success.

- [ ] **Step 3: Review the final diff for scope and asset safety.**

Run:

```powershell
git diff 71545f3..HEAD -- nginx-1.18.0/html/cityroam tests/frontend/cityroam-ui-contract.ps1
git status --short
```

Expected: only CityRoam frontend assets, the static test and the plan/spec commits are new; pre-existing logs, `application.yaml`, crash logs and `graphify-out/` are unstaged and uncommitted.

- [ ] **Step 4: Commit any validation-only correction.**

```powershell
git add nginx-1.18.0/html/cityroam tests/frontend/cityroam-ui-contract.ps1
git commit -m "fix: polish cityroam UI regression findings"
```

Do not create this commit when the validation step required no source changes.

## Plan Review

- Coverage: Task 1 establishes local, licensed assets and reusable UI primitives; Tasks 2-5 cover every existing HTML page and each agreed interaction boundary; Task 6 performs static, browser and backend regression verification.
- No API, database, Java, Nginx or configuration change is planned.
- Placeholder scan: no unbounded “later” work, unspecified source file, or new server capability remains.
- Consistency: every page consumes the shared assets from Task 1; unavailable map/message behaviour is implemented solely in the footer and verified by the static test.
