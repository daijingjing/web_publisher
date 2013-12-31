@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.5.2' )
@Grab(group='redis.clients', module='jedis', version='2.0.0' )
@Grab(group='org.apache.poi', module='poi', version='3.9' )


import groovy.json.*
import groovy.text.GStringTemplateEngine

import org.apache.poi.hssf.usermodel.HSSFWorkbook


import groovyx.net.http.HTTPBuilder
import static groovyx.net.http.Method.GET
import static groovyx.net.http.ContentType.TEXT
import static groovyx.net.http.ContentType.BINARY


class VideoSpider{

    String publish_dir = '/wasu/video';
    boolean fored_download = false;
    boolean debug_mode = false;
    int http_waitfor_second = 10;
    long max_duration = 600;   // 10 minutes

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // db functions
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////
    def pool_db = new redis.clients.jedis.JedisPool(new redis.clients.jedis.JedisPoolConfig(), "localhost");

    String genkey_video(def url) {
        return 'video:' + MD5(url.toLowerCase());
    }
    String genkey_date(Date date) {
        return 'video-date:' + date.format('yyyyMMdd');
    }
    String genkey_touchurl(String url) {
        return 'touch-url:' + MD5(url.toLowerCase());
    }

    def put_video(def url, def video) {
        def jedis = pool_db.getResource();

        try{
            def key = genkey_video(url);
            jedis.set(key, JsonOutput.toJson(video))
            jedis.sadd(genkey_date(video.DATE), url);
            jedis.zadd('video-url', 1, url);
        } finally {
            pool_db.returnResource(jedis);
        }
    }

    def exists_video(def url) {
        def jedis = pool_db.getResource();
        def result = null;

        try{
            result = jedis.zscore('video-url', url);
        } finally {
            pool_db.returnResource(jedis);
        }

        return result;
    }

    def get_video(def url) {
        def jedis = pool_db.getResource();
        def video = null;

        try{
            if (exists_video(url)) {
                video = new JsonSlurper().parseText(jedis.get(genkey_video(url)));
            }
        } finally {
            pool_db.returnResource(jedis);
        }

        return video;
    }
    
    def list_video(Date date) {
        def jedis = pool_db.getResource();
        def list = [];

        try{
            jedis.smembers(genkey_date(date)).each{url ->
                list << new JsonSlurper().parseText(jedis.get(genkey_video(url)));
            }
        } finally {
            pool_db.returnResource(jedis);
        }

        return list;
    }
    
    boolean touch_url(String url, boolean fored = false) {
        boolean exists = false;
        def jedis = pool_db.getResource();
        def key = genkey_touchurl(url);
        
        try{
            exists = jedis.exists(key);
            if (fored || !exists) {
                jedis.set(key, new Date().format('yyyyMMddHHmmss'))
                jedis.expire(key, 600 /* 10 minute */)
            }
        } finally {
            pool_db.returnResource(jedis);
        }
        
        return exists
    }
    
    void touch_download(String url) {
        def jedis = pool_db.getResource();
        def key = "download-host:" + new URL(url).host;
        
        try{
            while(jedis.exists(key)) {
                sleep(300);
            }
            
            jedis.set(key, new Date().format('yyyyMMddHHmmss'))
            jedis.expire(key, http_waitfor_second /* 5 second */)
            
        } finally {
            pool_db.returnResource(jedis);
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // checksum functions
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////
    def CRC32(def str) {
        def crc32 = new java.util.zip.CRC32();
        crc32.update(str.getBytes());
        return Long.toString(crc32.getValue(), 16);
    }
    def MD5(def str) {
        def md5 = java.security.MessageDigest.getInstance("MD5");
        md5.update(str.getBytes());

        return new BigInteger(1, md5.digest()).toString(16);
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // HTTP functions
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////
    // static PoolingClientConnectionManager connectionManager = new PoolingClientConnectionManager();
    def http_get(String url, def referer = null) {

        def responseBody = null

        
        try {
            print "[${new Date().format('yyyy/MM/dd HH:mm:ss')}] Get: ${url} ... "
            
            def http = new HTTPBuilder()
            
            http.client.params.setParameter(org.apache.http.params.CoreConnectionPNames.SO_TIMEOUT, 30000);
            http.client.params.setParameter(org.apache.http.params.CoreConnectionPNames.CONNECTION_TIMEOUT, 30000);
            http.client.params.setParameter(org.apache.http.params.CoreProtocolPNames.WAIT_FOR_CONTINUE, 100);
            http.client.params.setParameter(org.apache.http.params.CoreProtocolPNames.USE_EXPECT_CONTINUE, false);
            
    
            http.request( url, GET, TEXT ) { req ->
                // uri.path = '/ajax/services/search/web'
                // uri.query = [ v:'1.0', q: 'Calvin and Hobbes' ]
                headers.'Host'          = new URL(url).host
                headers.'User-Agent'    = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/29.0.1547.76 Safari/537.36"
                headers.'Accept'        = 'text/html'
                headers.'Referer'       = referer?referer.toString():url

                response.success = { resp, reader ->
                    // assert resp.statusLine.statusCode == 200
                    // println "Got response: ${resp.statusLine}"
                    // println "Content-Type: ${resp.headers.'Content-Type'}"
                    responseBody = reader.text
                }
            }
            println 'done!'
            
            touch_download(url); // keep speed
            
        } catch(java.net.SocketException ex) {
            println 'connect_closed!'
        } catch(java.net.SocketTimeoutException ex) {
            println 'read_timeout!'
        } catch(groovyx.net.http.HttpResponseException ex) {
            println 'Internal Server Error!'
        } catch(org.apache.http.conn.ConnectTimeoutException ex) {
            println 'connect_timeout!'
        } catch(java.io.FileNotFoundException ex) {
            println 'io_error!'
        } catch(Exception ex) {
            ex.printStackTrace();
            println 'error!'
        }


        return responseBody;
    }
    int http_download(String referer, String url, String file) {
                
        int length = 0;
        
        try {
            if (!fored_download && new File(file).exists())
                return 0;

            print "[${new Date().format('yyyy/MM/dd HH:mm:ss')}] Download: ${url} ... "
            
            def http = new HTTPBuilder()
            def host = new URL(url).host;
            
            http.client.params.setParameter(org.apache.http.params.CoreConnectionPNames.SO_TIMEOUT, 30000);
            http.client.params.setParameter(org.apache.http.params.CoreConnectionPNames.CONNECTION_TIMEOUT, 30000);
            http.client.params.setParameter(org.apache.http.params.CoreProtocolPNames.WAIT_FOR_CONTINUE, 100);
            http.client.params.setParameter(org.apache.http.params.CoreProtocolPNames.USE_EXPECT_CONTINUE, false);

            
            http.request( url, GET, BINARY ) { req ->
                headers.'Host'          = host
                headers.'User-Agent'    = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/29.0.1547.76 Safari/537.36"
                headers.'Accept'        = '*/*'
                headers.'Connection'    = 'keep-alive'
                headers.'Referer'       = referer ? referer.toString() : url

                response.success = { resp, reader ->
                    def fos = new FileOutputStream(file + '.tmp')
                    byte[] bytes = new byte[4096];
                    
                    for(;;) {
                        int size = reader.read(bytes);
                        if (size < 0) break;
                        fos.write(bytes, 0, size);
                        length += size;
                    }
                    
                    fos.flush();
                    fos.close();
                    new File(file + '.tmp').renameTo(file);
                }
            }
            
            println 'done!'

            touch_download(url); // keep speed
            
        } catch(java.net.SocketException ex) {
            println 'connect_closed!'
            length = -1;
        } catch(java.net.SocketTimeoutException ex) {
            println 'read_timeout!'
            length = -1;
        } catch(groovyx.net.http.HttpResponseException ex) {
            println 'Internal Server Error!'
            length = -1;
        } catch(org.apache.http.conn.ConnectTimeoutException ex) {
            println 'connect_timeout!'
            length = -1;
        } catch(java.io.FileNotFoundException ex) {
            println 'io_error!'
            length = -1;            
        } catch(Exception ex) {
            ex.printStackTrace();
            println 'error!'
            length = -1;
        }
            
        return length;
    }
    

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // extract functions
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////
    def extract_video(text) {
        def m = text =~ /(?m)var _playUrl = '(.*?\.mp4|3gp|flv)',/
        if (m) {
            return m[0][1];
        } else {
            return null
        }
    }
    def extract_title(text) {
        def m = text =~ /(?m)var _gsChannel = "(.*?)";/

        if (m) {
            def channels = m[0][1].split('/');

            if (channels.size() > 1) return channels[-1]
            else return null

        } else {
            return null
        }
    }
    def extract_desc(text) {
        def m = text =~ /(?m)<meta name="description" content="(.*?)">/
        if (m) {
            return m[0][1];
        } else {
            return null
        }
    }
    def extract_duration(text) {
        def m = text =~ /(?m)_playDuration = '(\d+)',/
        if (m) {
            return m[0][1].toLong();
        } else {
            return null
        }
    }
    def extract_published(text) {
        def m = text =~ /(?m)\u53d1\u5e03\u65f6\u95f4\uff1a<\/em>(\d{4}-\d{2}-\d{2})/
        if (m) {
            return Date.parse('yyyy-MM-dd', m[0][1]);
        } else {
            return null
        }
    }
    def extract_tags(text) {
        def tags = []
        def m = text =~ /(?m)<meta name="keywords" content="(.*?)">/

        if (m) {
            m[0][1].split('\uff0c| ').each{
                tags << it
            }
        }

        return tags;
    }
    def extract_channel(text) {
        def m = text =~ /(?m)var _gsChannel = "(.*?)";/

        if (m) {
            def channels = m[0][1].split('/');

            if (channels.size() > 1) return channels[1]
            else return null

        } else {
            return null
        }
    }
    def extract_list_item(text) {
        def items = []
        def idx1 = text.indexOf('<div id="publish"');
        def idx2 = text.indexOf('<div class="item_page">');

        if (idx1 > 0 && idx2 > 0) {
            def sub_content = text.substring(idx1,idx2);
            def m = sub_content =~ /(?m)<a .*? href='(http:\/\/www.wasu.cn\/Play\/show\/id\/.*?)'><img .*? original="(.*?)"/
            for(int i=0; m && i < m.count; i++) {
                items << ['URL': m[i][1],
                          'THUMB': m[i][2]
                         ]
            }
        }

        return items
    }

    

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // grab functions
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////
    def grab_article(def url, boolean fored = false, def referer = null) {

        def article = [:]

        if (fored || !touch_url(url)) {
            def text = http_get(url, referer)?:'';

            article['HOST'] = new URL(url).getHost();
            article['UUID'] = MD5(url.toLowerCase());
            article['CHANNEL'] = extract_channel(text);
            article['VIDEO'] = extract_video(text);
            article['TITLE'] = extract_title(text);
            article['DATE'] = extract_published(text);
            article['DESC'] = extract_desc(text);
            article['DURATION'] = extract_duration(text);
            article['TAG'] = extract_tags(text);
            article['ID'] = article['DATE']?.format('yyyyMMdd') + '_' + CRC32(url + article['TITLE']);
            
            if (!article.DESC || article.DESC == '\u4e2d\u56fd\u6700\u5927\u89c6\u9891\u95e8\u6237\u7f51\u7ad9\uff0c\u63d0\u4f9b\u6700\u65b0\u89c6\u9891\u65b0\u95fb\u3001\u9ad8\u6e05\u7535\u5f71\u7535\u89c6\u5267\u3001\u70ed\u95e8\u7efc\u827a\u5a31\u4e50\u8282\u76ee\u3001\u8d22\u7ecf\u3001\u6c7d\u8f66\u3001\u79d1\u6280\u3001\u98ce\u5c1a\u3001\u64ad\u5ba2\u3001\u4f53\u80b2\u3001\u52a8\u6f2b\u3001\u6e38\u620f\u7b49\u89c6\u9891\u3002\u514d\u8d39\u9ad8\u6e05\u89c6\u9891\u5728\u7ebf\u89c2\u770b\uff0c\u5c3d\u5728\u534e\u6570\u0054\u0056\u3002') {
                article.DESC = article.TITLE
            }
        }

        return article['VIDEO']?article:null;
    }

    long grab_list(def channel, def url, boolean fored = false) {

        long count = 0

        if (fored || !touch_url(url)) {
            def text = http_get(url)

            if (text) {
                extract_list_item(text).each { item ->
                    if (fored || !exists_video(item.URL)) {
                        def article = grab_article(item.URL, fored, url);
                        article?.putAll(item)

                        if (article && article.DURATION < max_duration) {   // skip long movies
                            article.CHANNEL = channel
                            
                            def dirs = publish_dir + '/' + article.DATE.format('yyyyMMdd');
                            new File(dirs).mkdirs();

                            int thumb_dw = -1;
                            int video_dw = -1;
                            // download thumb
                            def thumb_ext = article.THUMB.lastIndexOf('.') > 0 ? article.THUMB[article.THUMB.lastIndexOf('.') .. -1] : ''
                            thumb_dw = http_download(article.URL, article.THUMB, dirs + '/' + article.ID + thumb_ext.toLowerCase())
                            article.THUMB_FILE = dirs + '/' + article.ID + thumb_ext.toLowerCase()

                            // download video
                            def video_ext = article.VIDEO.lastIndexOf('.') > 0 ? article.VIDEO[article.VIDEO.lastIndexOf('.') .. -1] : ''
                            video_dw = http_download(article.URL, article.VIDEO, dirs + '/' + article.ID + video_ext.toLowerCase());
                            article.VIDEO_FILE = dirs + '/' + article.ID + video_ext.toLowerCase()

                            // store2db
                            if (video_dw >= 0 && thumb_dw >= 0 && !debug_mode) {
                                put_video(item.URL, article);
                                count++
                            }
                            
                        }
                    }
                }
            }
        }

        return count
    }
    
    
    
    
    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // publish functions
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////
    def publish(Date date) {
        
        try {
            def videos = list_video(date).findAll{
                try {
                    return new File(it.VIDEO_FILE).exists() && new File(it.THUMB_FILE).exists() && it.DURATION < max_duration; // skip long movies
                } catch (Exception ex) {
                    return false;
                }
            };
            
            def f = new File('index.tpl')
            def template = new GStringTemplateEngine()
                                .createTemplate(f)
                                .make(['videos': videos, 'date': date, 'channels': videos.collect{it.CHANNEL}.unique()])

            def dirs = publish_dir + '/html'
            new File(dirs).mkdirs();
            
            dirs = dirs + "/${date.format('yyyyMMdd')}.html";
            println "[${new Date().format('yyyy/MM/dd HH:mm:ss')}] Publish HTML: ${dirs} ... "
            
            def fos = new FileOutputStream(dirs)
            fos.write(template.toString().getBytes('UTF-8'));
            fos.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        
    }
    
    def publish_xls(Date date) {
        try {
            def dirs = publish_dir + '/' + date.format('yyyyMMdd')
            new File(dirs).mkdirs();
            
            dirs = dirs + "/${date.format('yyyyMMdd')}.xls";
            
            println "[${new Date().format('yyyy/MM/dd HH:mm:ss')}] Publish XLS: ${dirs} ... "
            
            def wb = new HSSFWorkbook();
            def createHelper = wb.getCreationHelper();
            def sheet = wb.createSheet("sheet1");

            short rowIdx = 0
            
            // first row - title
            sheet.createRow(rowIdx).createCell(0).setCellValue('移动');
            def row1 = sheet.createRow(++rowIdx)
            
            // second row - head
            row1.createCell(0).setCellValue('实体文件名称');
            row1.createCell(1).setCellValue('移动内容名称');
            row1.createCell(2).setCellValue('移动内容简介');
            row1.createCell(3).setCellValue('节目类型');

            def videos = list_video(date).findAll{
                try {
                    return new File(it.VIDEO_FILE).exists() && new File(it.THUMB_FILE).exists() && it.DURATION < max_duration; // skip long movies
                } catch (Exception ex) {
                    return false;
                }
            };

            videos.eachWithIndex { item,idx ->
                // data row
                def row = sheet.createRow(++rowIdx)
                def desc = item.DESC
                if (!desc || desc == '\u4e2d\u56fd\u6700\u5927\u89c6\u9891\u95e8\u6237\u7f51\u7ad9\uff0c\u63d0\u4f9b\u6700\u65b0\u89c6\u9891\u65b0\u95fb\u3001\u9ad8\u6e05\u7535\u5f71\u7535\u89c6\u5267\u3001\u70ed\u95e8\u7efc\u827a\u5a31\u4e50\u8282\u76ee\u3001\u8d22\u7ecf\u3001\u6c7d\u8f66\u3001\u79d1\u6280\u3001\u98ce\u5c1a\u3001\u64ad\u5ba2\u3001\u4f53\u80b2\u3001\u52a8\u6f2b\u3001\u6e38\u620f\u7b49\u89c6\u9891\u3002\u514d\u8d39\u9ad8\u6e05\u89c6\u9891\u5728\u7ebf\u89c2\u770b\uff0c\u5c3d\u5728\u534e\u6570\u0054\u0056\u3002') {
                    desc = item.TITLE
                }
                
                
                row.createCell(0).setCellValue(item.ID);        // id
                row.createCell(1).setCellValue(item.TITLE);     // title
                row.createCell(2).setCellValue(desc);           // desc
                row.createCell(3).setCellValue(item.CHANNEL);   // channel
            }
        
            def fos = new FileOutputStream(dirs);
            wb.write(fos);
            fos.close();
            
            def fos2 = new FileOutputStream(publish_dir + '/' + date.format('yyyyMMdd') + '/success.txt');
            fos2.write(0);
            fos2.close();
            
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    
    
    
    
    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // main business functions
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////
    static void main(String[] args) {
        def cli = new CliBuilder()  
        cli.h( longOpt: 'help', required: false, '显示当前帮助信息' )  
        cli.f( longOpt: 'force', required: false, args: 0, '强制抓取' )  
        cli.d( longOpt: 'publish-date', argName: 'date', required: false, args: 1, '发布日期(yyyyMMdd)' )  
        cli.p( longOpt: 'page', argName: 'page', required: false, args: 1, '爬取页数' )  
        cli.l( longOpt: 'force-download', required: false, args: 0, '强制下载' )  
        cli.b( longOpt: 'base-path', argName: 'publish', required: false, args: 1, '发布路径' )  
        cli.w( longOpt: 'wait-for', argName: 'wait-for', required: false, args: 1, 'HTTP连接等待间隔(秒)' )  
        cli.s( longOpt: 'max-duration', argName: 'max-duration', required: false, args: 1, '过滤视频最大时长(秒)' )  
        cli.x( longOpt: 'debug', argName: 'debug', required: false, args: 0, '调试模式' )  
          
        def opt = cli.parse(args)  
        if (!opt) return;
        if (opt.h) {  
            cli.usage();
            return
        }
        
        boolean fored = false;
        int page = 1
        Date date = new Date();
        
        def instance = new VideoSpider();

        if (opt.d) date = Date.parse('yyyyMMdd', opt.d);
        if (opt.p) page = opt.p.toInteger();
        if (opt.f) fored = true;
        if (opt.l) instance.fored_download = true;
        if (opt.b) instance.publish_dir = opt.b;
        if (opt.w) instance.http_waitfor_second = opt.w.toInteger();
        if (opt.s) instance.max_duration = opt.s.toLong();
        if (opt.x) instance.debug_mode = true;
        
        println "[${new Date().format('yyyy/MM/dd HH:mm:ss')}] begin spider (debug mode:${instance.debug_mode})... "
        long sum = 0;
        [
            ['http://all.wasu.cn/index/cid/22',                         '资讯'],
            ['http://all.wasu.cn/index/sort/time/cid/28/class/program', '综艺'],
            ['http://all.wasu.cn/index/cid/32',                         '体育'],
            ['http://all.wasu.cn/index/sort/time/cid/27/class/program', '娱乐'],
            ['http://www.wasu.cn/list/index/cid/1/class/notice',        '影视'], //- 电影预告片
            ['http://all.wasu.cn/index/cid/11/class/sidelights',        '影视'], //- TV速递
            ['http://all.wasu.cn/index/sort/time/cid/30/class/program', '影视']  //- 影视资讯

        ].each{ task ->
            sum += instance.grab_list(task[1], task[0], fored);
            for(int p=2; p < page + 1; p++) {
                sum += instance.grab_list(task[1], task[0] + '?p='+p, fored);
            }
        }


        if (!instance.debug_mode) {
            instance.publish(date);
            instance.publish_xls(date);
        }
        
        println "[${new Date().format('yyyy/MM/dd HH:mm:ss')}] Finished: $sum video information saved!"
    }

}
