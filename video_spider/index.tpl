<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>${date.format('yyyy/MM/dd')} - 采集结果列表</title>
    <meta name="viewport" content="width=device-width;initial-scale=1.0;minimum-scale=1.0;maximum-scale=1.0" />
    <meta name="MobileOptimized" content="" />
    <link type="text/css" rel="stylesheet" href="../css/jquery-ui.min.css" />
    <link type="text/css" rel="stylesheet" href="../css/style.css" />
    <script type="text/javascript" src="../js/jquery.min.js"></script>
    <script type="text/javascript" src="../js/jquery-ui.min.js"></script>
    <script type="text/javascript" src="../js/publish.js"></script>
</head>
<body>
<div class="body">
    <ul>
        <% channels.each { channel -> %>
        <li><a href="#channle-${channel}"><span>${channel}</span></a></li>
        <% } %>
    </ul>
    <% channels.each { channel -> %>
    <div id="channle-${channel}" class="channel">
        <h1 class="channel-name">${channel}</h1>
        <% videos.findAll{it.CHANNEL == channel}.each { video -> %>
        <div class="video">
            <video class="player" preload="none" poster="/video/${date.format('yyyyMMdd')}/${video.ID}.jpg" src="/video/${date.format('yyyyMMdd')}/${video.ID}.mp4" controls="yes"></video>
            <p class="title">${video.TITLE}</p>
            <p class="id"><strong>编号：</strong><span>${video.ID}</span></p>
            <p class="duration"><strong>时长：</strong>${(video.DURATION/60).toInteger()}分${video.DURATION%60}秒</p>
            <p class="tag"><strong>关键字：</strong><span>${video.TAG.findAll{it.size() < 10}.join('</span><span>')}</span></p>
            <p class="desc"><strong>描述：</strong>${video.DESC}</p>
            <div style="clear:both;"></div>
        </div>
        <% } %>
        <div style="clear:both;"></div>
    </div>
    <% } %>
    
    <div style="clear:both;"></div>
    
</div>
<div class="publish-date">发布时间：${new Date().format('yyyy/MM/dd HH:mm:ss')}</div>
</body>
</html>
