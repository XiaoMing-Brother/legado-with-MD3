# 从上游 HapeLee/legado-with-MD3 拉取更新，合并到当前分支；不会向原仓库推送。
# 合并完成后如需备份到 fork：git push xiaoming

$ErrorActionPreference = "Stop"
$UpstreamRemote = "upstream"
$PushRemote = "xiaoming"
$Branch = "main"

function Ensure-Remote($name, $fetchUrl) {
    $remotes = git remote
    if ($remotes -notcontains $name) {
        git remote add $name $fetchUrl
        git remote set-url --push $name "DISABLED-DO-NOT-PUSH-TO-UPSTREAM"
        Write-Host "Added remote '$name' -> $fetchUrl (fetch only)"
    }
}

Ensure-Remote "upstream" "https://github.com/HapeLee/legado-with-MD3.git"

Write-Host "Fetching $UpstreamRemote..."
git fetch $UpstreamRemote

$mergeRef = "$UpstreamRemote/$Branch"
Write-Host "Merging $mergeRef into $(git branch --show-current)..."
git merge $mergeRef

Write-Host ""
Write-Host "Done. Push your fork only with:"
Write-Host "  git push $PushRemote $Branch"
