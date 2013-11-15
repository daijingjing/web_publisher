#!/bin/bash
#
source /etc/profile
source ~/.bash_profile
cd /home/daijj/video_spider
nohup groovy /home/daijj/video_spider/video_spider.groovy >> /home/daijj/video_spider/video_spider.log 2>&1 &

