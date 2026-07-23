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

$travelCss = Get-Content -Raw (Join-Path $root 'css/travel-ui.css')
if ($travelCss -notmatch '(?s)\.travel-page\s*\{[^}]*box-sizing:\s*border-box') { throw 'travel-page must use border-box sizing' }

$footer = Get-Content -Raw (Join-Path $root 'js/footer.js')
if ($footer -notmatch "comingSoon\('漫游地图'\)") { throw 'Map navigation is not a coming-soon action' }
if ($footer -notmatch "comingSoon\('消息中心'\)") { throw 'Message navigation is not a coming-soon action' }
if ($footer -notmatch 'location\.href = "/blog-edit\.html"') { throw 'Publish navigation changed' }

Write-Output 'CityRoam frontend shared UI contract passed.'
