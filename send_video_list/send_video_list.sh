#!/bin/sh

source /etc/profile
source ~/.bash_profile

cd /home/daijj/send_video_list

groovy -c utf-8 sendVideoList.groovy 

