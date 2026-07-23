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

Write-Output 'CityRoam frontend shared UI contract passed.'
