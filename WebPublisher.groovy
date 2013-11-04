import groovy.text.GStringTemplateEngine

class WebPublisher {
    String publish_base = '/var/www/html/'
    
    Db db = new Db()
    boolean forced = false
    String templatePath = './templates'
    
    
    class Publish {
        String template = ''
        int page = 0
        boolean hasMore = false
        String uuid = ''
        String channel = ''
        
        def data = [:]
        
        def next = null
        def prev = null
    }
    
    WebPublisher() {
        if (new File('zore_vids').exists()) {
            print "Remove invalidation video "
            new File('zore_vids').readLines().each{ line ->
                print '.'
                db.deleteVideoByVID(line);
            }
            println 'done!'
        }
    }
    
    int publishContent() {
        int count = 0;
        print "start publish content page "
        def video_list = db.listVideos() as List
        
        for (int i=0; i < video_list.size(); i++) {
            def uuid = video_list[i]
            
            if (forced || !new File(publish_base + urlVideo(uuid)).exists()) { // skip exist content page
                print '.'
                def bytes = new File(templatePath + '/content.html').readBytes()
                def template = new String(bytes, 'UTF-8').replace('$', '\\$')
                def video = db.findVideo(uuid)
                
                Publish task = new Publish('template': template, 'channel': video.channel)
                
                if (i > 0) { 
                    task.next = db.findVideo(video_list[i-1]); 
                    if (task.next.channel != video.channel) {
                        task.next = null;
                    }
                }
                if (i < video_list.size() - 1) { 
                    task.prev = db.findVideo(video_list[i+1]); 
                    if (task.prev.channel != video.channel) {
                        task.prev = null;
                    }
                }
                
                task.data.putAll(video)
                
                Utils.mkdirs4file(publish_base + urlVideo(uuid))
                new FileOutputStream(publish_base + urlVideo(uuid)).withWriter('UTF-8'){ w ->
                    w << publishTask(task);
                }
                count++
            }
            
        }
        println " done!"
        
        return count
    }
    
    int publishIndex() {
        int count = 0;
        print "start publish list page "
        db.findChannels().each { channel ->
            def bytes = new File(templatePath + '/list.html').readBytes()
            def template = new String(bytes, 'UTF-8').replace('$', '\\$')
            
            Publish task = new Publish('template': template, 'channel': channel)
            
            // publish pages
            while(true) {
                print '.'
                Utils.mkdirs4file(publish_base + urlChannel(channel,task.page))
                new FileOutputStream(publish_base + urlChannel(channel,task.page)).withWriter('UTF-8'){ w ->
                    w << publishTask(task);
                }
                count++
                
                if (!task.hasMore)
                    break;

                task.page++;
                task.hasMore = false;
            }

        }
        println " done!"

        return count;
    }
    
    def publishHomepage() {
        print "start publish home page "
        def bytes = new File(templatePath + '/index.html').readBytes()
        def template = new String(bytes, 'UTF-8').replace('$', '\\$')
        
        Publish task = new Publish('template': template)

        Utils.mkdirs4file(publish_base + '/index.html')
        new FileOutputStream(publish_base + '/index.html').withWriter('UTF-8'){ w ->
            w << publishTask(task);
        }        
        print '.'
        println " done!"
    }
    
    int pagesChannel(String channel, int max = 10) {
        int count = db.countVideosByChannel(channel)
        return Math.floor((count + max / 2)/max)
    }
    
    def urlChannel(String channel, def page = null) {
        String id = Utils.encodeAsMD5Hex(channel)
        
        return '/c/' + id + '/' + 'index' + (page > 0 ? "_$page" : '') + '.html'
    }
    
    def urlVideo(String uuid) {
        return '/v/' + uuid + '.html'
    }
    
    def urlVideoPoster(String uuid) {
        return '/v/p/' + uuid + '.jpg'
    }
    
    def publishTask(def task) {
        def binding = ['service': this, 'task': task]
        def engine = new GStringTemplateEngine()
        def template = engine.createTemplate(task.template).make(binding)
        return template.toString()
    }

    static void main(String[] args) {
        def cli = new CliBuilder()  
        cli.h( longOpt: 'help', required: false, '显示当前帮助信息' )  
        cli.p( longOpt: 'publish-path', argName: 'publish-path', required: false, args: 1, '发布路径' )  
        cli.m( longOpt: 'templates', argName: 'templates', required: false, args: 1, '模板路径' )  
        cli.f( longOpt: 'force', required: false, args: 0, '强制发布' )  
        cli.d( longOpt: 'publish-date', argName: 'publish-date', required: false, args: 1, '发布日期[yyyyMMdd]' )  
          
          
        def opt = cli.parse(args)  
        if (!opt) return;
        if (opt.h) {  
            cli.usage();
            return  
        }

        
        def publisher = new WebPublisher();
        
        if (opt.p) publisher.publish_base = opt.p;
        if (opt.m) publisher.templatePath = opt.m;
        if (opt.f) publisher.forced = true;
        
        if (publisher.publishContent() > 0) {
            publisher.publishIndex()
            publisher.publishHomepage()
        } else {
            println "no more content to publish!"
        }
    }

}
