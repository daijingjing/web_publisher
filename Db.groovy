import groovy.sql.Sql

/*********************************************************
 *
 * database init table script
 *
 *********************************************************

CREATE TABLE  `video_resource` (
    `uuid` CHAR( 32 ) NOT NULL ,
    `channel` VARCHAR( 50 ) NULL ,
    `orgChannel` VARCHAR( 50 ) NULL ,
    `vid` VARCHAR( 50 ) NULL ,
    `title` VARCHAR( 200 ) NULL ,
    `desc` TEXT NULL ,
    `published` DATETIME NULL,
    PRIMARY KEY (  `uuid` )
);
ALTER TABLE  `video_resource` ADD INDEX `vid`       (  `vid` ASC );
ALTER TABLE  `video_resource` ADD INDEX `published` (  `published` ASC );
ALTER TABLE  `video_resource` ADD INDEX `channel`   (`channel` ASC ) ;


CREATE TABLE  `video_keyword` (
    `uuid` CHAR( 32 ) NOT NULL ,
    `keyword` VARCHAR( 50 ) NOT NULL
);
ALTER TABLE  `video_keyword` ADD INDEX (  `uuid` );
ALTER TABLE  `video_keyword` ADD INDEX (  `keyword` );

*/

class Db {

    String driver = 'com.mysql.jdbc.Driver'

    String db_host = 'localhost';
    String db_name = 'video_resource';
    String db_user = 'root';
    String db_pass = '1q2w3e';

    def sqlInstance = null;
    
    Db() {
        db_host = Config.setting.db_host
        db_name = Config.setting.db_name
        db_user = Config.setting.db_user
        db_pass = Config.setting.db_pass
        
        sqlInstance = Sql.newInstance("jdbc:mysql://${db_host}/${db_name}?useUnicode=true&characterEncoding=utf8", 
                              db_user, db_pass, driver )
    }

    Db(String host, String name, String user, String pass) {
        db_host = host
        db_name = name
        db_user = user
        db_pass = pass
        
        sqlInstance = Sql.newInstance("jdbc:mysql://${db_host}/${db_name}?useUnicode=true&characterEncoding=utf8", 
                              db_user, db_pass, driver )
    }
    
    def InitTables() {
        String sql = """
        """;
        
        sqlInstance.execute(sql);
    }
    
    boolean addVideo(def video) {
    
        def found = sqlInstance.firstRow('SELECT `uuid` FROM `video_resource` WHERE `vid` = ?', [video.vid]);
    
        def channelMap = [
            '新闻'   : '资讯',
            '传奇'   : '资讯',
            '名栏目' : '综艺',
            '时尚'   : '娱乐',
            '音乐'   : '娱乐' ]
            
            
        def chnl = channelMap[video.channel]

        if (!found) {
            sqlInstance.execute("INSERT INTO `video_resource` (`uuid`, `channel`, `orgChannel`, `vid`, `title`, `desc`, `published`) values (?, ?, ?, ?, ?, ?, ?)",
                                [video.uuid, chnl?:video.channel, video.channel, video.vid, video.title, video.desc, video.published])
                        
            video.keywords.each { keyword ->
                addVideoKeyword(video.uuid, keyword)
            }
            
            return true;
        }
        
        return false;
    }
    
    void addVideoKeyword(String uuid, String keyword) {
        sqlInstance.execute("INSERT INTO `video_keyword` (`uuid`, `keyword`) values (?, ?)",
                            [uuid, keyword])
    }
    
    def findChannels() {
        def channels = []
        
        sqlInstance.eachRow( 'SELECT DISTINCT `channel` FROM `video_resource`' ) { 
            channels.add(it[0]) 
        }
        
        return channels;
    }
    
    def findVideos(def params) {
        params.offset = params.offset?:0
        params.max = params.max?:100
        
        def videos = []
        sqlInstance.eachRow( "SELECT * FROM `video_resource` ORDER BY published DESC,vid DESC LIMIT ${params.offset.toInteger()},${params.max.toInteger()}" ) { 
            videos.add(it.toRowResult())
        }
        
        return videos
    }
    
    def countVideosByChannel(String channel) {
        return sqlInstance.firstRow( "SELECT COUNT(`uuid`) FROM `video_resource` WHERE `channel` = ?", [channel] )[0].toLong()
    }
    
    def findVideo(String uuid){
        return sqlInstance.firstRow( "SELECT * FROM `video_resource` WHERE `uuid` = ?", [uuid] )
    }
    
    def listVideos() {
        def videos = []
        sqlInstance.eachRow( "SELECT `uuid` FROM (SELECT * FROM `video_resource` ORDER BY published DESC LIMIT 1500) a ORDER BY channel,published DESC,vid DESC" ) { 
            videos.add(it[0])
        }
        
        return videos
    }
    
    def listVideosAll() {
        def videos = []
        sqlInstance.eachRow( "SELECT `uuid` FROM (SELECT * FROM `video_resource` ORDER BY published DESC) a ORDER BY channel,published DESC,vid DESC" ) { 
            videos.add(it[0])
        }
        
        return videos
    }
    
    def findVideosByDate(Date date) {
        def videos = []
        sqlInstance.eachRow( "SELECT `uuid` FROM `video_resource` WHERE `published` = ? ORDER BY channel,published DESC,vid DESC", [date] ) { 
            videos.add(it[0])
        }
        
        return videos
    }
    
    def deleteVideoByVID(String vid) {
        sqlInstance.execute( "DELETE FROM `video_resource` WHERE `vid` = ? ", [vid] )
    }
    
    def findVideosByChannel(String channel, def params) {
        params.offset = params.offset?:0
        params.max = params.max?:100
        
        def videos = []
        sqlInstance.eachRow( "SELECT * FROM `video_resource` WHERE `channel` = ? ORDER BY published DESC,vid DESC LIMIT ${params.offset.toInteger()},${params.max.toInteger()}", [channel] ) { 
            videos.add(it.toRowResult())
        }
        
        return videos
    }
    
    def findRelationVideo(String uuid, String channel, int max = 10) {
        def videos = []
        
        sqlInstance.eachRow( "SELECT b.* FROM (SELECT DISTINCT `uuid` FROM `video_keyword` WHERE `keyword` IN (SELECT `keyword` FROM `video_keyword` WHERE `uuid` = ?) AND `uuid` <> ?) a INNER JOIN video_resource b ON a.uuid = b.uuid ORDER BY b.published DESC LIMIT 0,${max.toInteger()}", [uuid,uuid] ) { 
            videos.add(it.toRowResult())
        }
        
        if (videos.size() < max) {
            sqlInstance.eachRow( "SELECT * FROM `video_resource` WHERE `channel` = ? AND `uuid` <> ? ORDER BY published DESC,vid DESC LIMIT 0,${max - videos.size()}", [channel,uuid] ) { 
                videos.add(it.toRowResult())
            }
        }
        
        return videos
    }
    
}
