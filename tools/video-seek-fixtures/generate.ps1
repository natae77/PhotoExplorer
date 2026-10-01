param(
    [Parameter(Mandatory = $true)]
    [string] $FfmpegPath,
    [string] $OutputDirectory = "build/video-seek-fixtures"
)

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$font = "C\:/Windows/Fonts/arial.ttf"
$label = "drawtext=fontfile='$font':text='frame %{n}':fontcolor=white:fontsize=44:" +
    "box=1:boxcolor=black@0.7:x=(w-text_w)/2:y=(h-text_h)/2"

& $FfmpegPath -hide_banner -loglevel error -y `
    -f lavfi -i "testsrc2=size=320x240:rate=10:duration=2" `
    -vf $label -c:v libx264 -pix_fmt yuv420p -g 20 -bf 2 `
    (Join-Path $OutputDirectory "cfr-bframes.mp4")
if ($LASTEXITCODE -ne 0) { throw "CFR fixture generation failed" }

# Presentation times: 0, 40, 120, 200, 450, 500 and 850 ms.
$vfrPts = "if(eq(N\,0)\,0\,if(eq(N\,1)\,.04/TB\,if(eq(N\,2)\,.12/TB\," +
    "if(eq(N\,3)\,.20/TB\,if(eq(N\,4)\,.45/TB\,if(eq(N\,5)\,.50/TB\," +
    "if(eq(N\,6)\,.85/TB\,1.20/TB)))))))"
& $FfmpegPath -hide_banner -loglevel error -y `
    -f lavfi -i "testsrc2=size=320x240:rate=10:duration=0.81" `
    -vf "$label,settb=1/1000,setpts=$vfrPts" -fps_mode vfr -c:v libx264 -pix_fmt yuv420p -g 8 -bf 2 `
    (Join-Path $OutputDirectory "vfr-bframes.mp4")
if ($LASTEXITCODE -ne 0) { throw "VFR fixture generation failed" }
