# 终端滚动修复脚本
# 解决PowerShell终端长输出滚动问题

# 设置更好的终端缓冲区
function Set-TerminalBuffer {
    try {
        # 设置控制台缓冲区大小
        $bufferSize = New-Object System.Management.Automation.Host.Size(120, 9999)
        $host.UI.RawUI.BufferSize = $bufferSize
        Write-Host "终端缓冲区已设置为: $($bufferSize.Width) x $($bufferSize.Height)" -ForegroundColor Green
    } catch {
        Write-Host "设置缓冲区时出错: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

# 改进的tree命令函数，支持自动分页
function Tree-Fixed {
    param(
        [string]$Path = ".",
        [switch]$Files = $true
    )
    
    # 确保终端缓冲区足够大
    Set-TerminalBuffer
    
    # 执行tree命令并捕获输出
    $treeOutput = if ($Files) {
        tree $Path /f
    } else {
        tree $Path
    }
    
    # 分页显示输出
    $lines = $treeOutput -split "`n"
    $pageSize = [Math]::Min(30, $Host.UI.RawUI.WindowSize.Height - 5)
    $currentLine = 0
    
    while ($currentLine -lt $lines.Count) {
        # 显示一页内容
        for ($i = 0; $i -lt $pageSize -and $currentLine -lt $lines.Count; $i++) {
            Write-Output $lines[$currentLine]
            $currentLine++
        }
        
        # 如果还有更多内容，等待用户按键
        if ($currentLine -lt $lines.Count) {
            Write-Host "`n-- 按任意键继续显示更多内容，按 Q 键退出 --" -ForegroundColor Cyan -NoNewline
            $key = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
            
            if ($key.Character -eq 'q' -or $key.Character -eq 'Q') {
                Write-Host "`n已退出显示。" -ForegroundColor Yellow
                break
            }
            Write-Host "`n" # 换行
        }
    }
}

# 自动滚动到底部的函数
function Scroll-ToBottom {
    try {
        # 尝试将光标移到底部
        $windowHeight = $Host.UI.RawUI.WindowSize.Height
        $bufferHeight = $Host.UI.RawUI.BufferSize.Height
        
        if ($bufferHeight -gt $windowHeight) {
            # 计算底部位置
            $bottomPos = $bufferHeight - $windowHeight
            $newPosition = New-Object System.Management.Automation.Host.Coordinates(0, $bottomPos)
            $Host.UI.RawUI.CursorPosition = $newPosition
        }
    } catch {
        # 静默处理错误
    }
}

# 创建tree命令的别名
Set-Alias -Name treef -Value Tree-Fixed -Force

# 初始化终端设置
Set-TerminalBuffer

Write-Host "终端滚动修复脚本已加载！" -ForegroundColor Green
Write-Host "使用方法:" -ForegroundColor Cyan
Write-Host "  treef          - 显示目录结构（自动分页）" -ForegroundColor White
Write-Host "  treef /f       - 显示目录结构及文件（自动分页）" -ForegroundColor White
Write-Host "  Set-TerminalBuffer - 手动设置终端缓冲区" -ForegroundColor White
Write-Host "  Scroll-ToBottom    - 滚动到终端底部" -ForegroundColor White