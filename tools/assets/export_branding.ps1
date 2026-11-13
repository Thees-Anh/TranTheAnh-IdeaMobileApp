param([switch]$Check)
$ErrorActionPreference="Stop"
Add-Type -AssemblyName System.Drawing
$brandRoot=if($PSScriptRoot) {Split-Path (Split-Path $PSScriptRoot -Parent) -Parent} else {(Get-Location).Path}
$brandRes=Join-Path $brandRoot "androidApp\src\main\res"
function Save-BrandBitmap($bitmap,$path) {
    $stream=New-Object System.IO.MemoryStream
    try {
        $bitmap.Save($stream,[System.Drawing.Imaging.ImageFormat]::Png);$bytes=$stream.ToArray()
        if($Check) {
            if(!(Test-Path -LiteralPath $path)) {throw "Missing $path"}
            $actual=[System.IO.File]::ReadAllBytes($path)
            if([Convert]::ToBase64String($actual) -ne [Convert]::ToBase64String($bytes)) {throw "Stale $path"}
        } else {
            [System.IO.Directory]::CreateDirectory((Split-Path $path -Parent)) | Out-Null
            [System.IO.File]::WriteAllBytes($path,$bytes)
        }
    } finally {$stream.Dispose()}
}
function Export-Brand($image,$size,$path,$tile=$false,$round=$false) {
    $bitmap=New-Object System.Drawing.Bitmap $size,$size
    $g=[System.Drawing.Graphics]::FromImage($bitmap)
    $g.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode=[System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.PixelOffsetMode=[System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    try {
        if($tile) {
            $shape=New-Object System.Drawing.Drawing2D.GraphicsPath;$r=[single]($size*0.44)
            if($round) {$shape.AddEllipse(0,0,$size,$size)} else {
                $shape.AddArc(0,0,$r,$r,180,90);$shape.AddArc(($size-$r),0,$r,$r,270,90)
                $shape.AddArc(($size-$r),($size-$r),$r,$r,0,90);$shape.AddArc(0,($size-$r),$r,$r,90,90);$shape.CloseFigure()
            }
            $gradient=New-Object System.Drawing.Drawing2D.LinearGradientBrush ([System.Drawing.Point]::new(0,0)),([System.Drawing.Point]::new($size,$size)),([System.Drawing.ColorTranslator]::FromHtml("#A48BDA")),([System.Drawing.ColorTranslator]::FromHtml("#63469F"))
            $g.FillPath($gradient,$shape);$g.SetClip($shape)
            $g.DrawImage($image,[single]($size*.06),[single]($size*.06),[single]($size*.88),[single]($size*.88))
            $gradient.Dispose();$shape.Dispose()
        } else {$g.DrawImage($image,0,0,$size,$size)}
        Save-BrandBitmap $bitmap $path
    } finally {$g.Dispose();$bitmap.Dispose()}
}
foreach($name in @("cat_normal","cat_hungry","cat_bored","puppy","rabbit","capybara")) {
    $image=[System.Drawing.Image]::FromFile((Join-Path $brandRoot "design\branding\masters\$name.png"))
    try {
        Export-Brand $image 256 (Join-Path $brandRes "drawable-xxxhdpi\pawquest_portrait_$name.png")
        if($name.StartsWith("cat_")) {
            $mood=$name.Substring(4)
            Export-Brand $image 192 (Join-Path $brandRes "drawable-xxxhdpi\pawquest_legacy_$mood.png") $true
            Export-Brand $image 512 (Join-Path $brandRoot "design\branding\previews\$mood.png") $true
            if($mood -eq "normal") {
                $densities=@{mdpi=48;hdpi=72;xhdpi=96;xxhdpi=144;xxxhdpi=192}
                foreach($density in $densities.Keys) {
                    Export-Brand $image $densities[$density] (Join-Path $brandRes "mipmap-$density\ic_launcher.png") $true
                    Export-Brand $image $densities[$density] (Join-Path $brandRes "mipmap-$density\ic_launcher_round.png") $true $true
                }
            }
        }
    } finally {$image.Dispose()}
}
Write-Output "PASS technical PNG density exports"
