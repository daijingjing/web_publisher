import org.apache.commons.mail.*
import javax.mail.internet.MimeUtility
import org.apache.poi.hssf.usermodel.*
import org.apache.poi.hssf.util.CellRangeAddress
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.Font
import groovy.sql.Sql
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import java.nio.channels.FileChannel


def getVideoList(def date) {
    def videos = []

    def driver = 'com.mysql.jdbc.Driver'
    def db_host = 'localhost';
    def db_name = 'video';
    def db_user = 'root';
    def db_pass = '1q2w3e';

    def sqlInstance = Sql.newInstance("jdbc:mysql://${db_host}/${db_name}?useUnicode=true&characterEncoding=utf8", 
                          db_user, db_pass, driver )

    date.clearTime()
    sqlInstance.eachRow( "SELECT * FROM `video_resource` WHERE `published` = ? ORDER BY channel,published DESC,vid DESC", [date] ) { 
        videos.add(it.toRowResult())
    }
    
    return videos
}

def sendPackage(def date, def fileName, def to) {
    def ext = ''
    
    if (fileName.lastIndexOf('.') > 0) {
        ext = fileName.substring(fileName.lastIndexOf('.'))
    }
    
    // Create the email message 
    HtmlEmail  email = new HtmlEmail (); 

    email.setCharset("UTF-8");
    email.setHostName("mail.temobi.com"); 
    email.setAuthentication("jinxia@temobi.com","081130"); 
    email.setFrom("jinxia@temobi.com", "靳霞"); 
    // add to
    to.each{
        email.addTo(it[0],it[1]);
    }
    

    email.setSubject(MimeUtility.encodeWord("视频内容报备(${date.format('yyyy/MM/dd')})","UTF-8",null)); 

    email.setHtmlMsg("""<div style="FONT-SIZE: 11pt; FONT-FAMILY: 新宋体; COLOR: rgb(0,0,0)"><div>王俊，你好！</div><div>&nbsp;</div><div>&nbsp; &nbsp; 附件是视频应用上线发布的视频内容，特此报备！</div><div>&nbsp;</div></div><div><hr style="HEIGHT: 1px; WIDTH: 210px" align="left" color="#b5c4df" size="1"></div><div style="FONT-SIZE: 10pt; FONT-FAMILY: 新宋体; COLOR: rgb(0,0,0)">山西办事处 靳霞</div><div style="FONT-SIZE: 10pt; FONT-FAMILY: 新宋体; COLOR: rgb(0,0,0)">-------------------------------------------</div><div style="FONT-SIZE: 10pt; FONT-FAMILY: 新宋体; COLOR: rgb(0,0,0)">深圳市融创天下科技股份有限公司</div><div style="FONT-SIZE: 10pt; FONT-FAMILY: 新宋体; COLOR: rgb(0,0,0)">ShenZhen Temobi Science &amp; Tech Co., Ltd</div><div style="FONT-SIZE: 10pt; FONT-FAMILY: 新宋体; COLOR: rgb(0,0,0)">WWW：<a href="http://www.temobi.com" target="_blank">www.temobi.com</a></div><div style="FONT-SIZE: 10pt; FONT-FAMILY: 新宋体; COLOR: rgb(0,0,0)">ADD：深圳市南山区科技南十二路18号长虹科技大厦19楼01-11单元</div><div style="FONT-SIZE: 10pt; FONT-FAMILY: 新宋体; COLOR: rgb(0,0,0)">MOB：15235378516</div>"""); 

    // add the attachment 
    EmailAttachment attachment = new EmailAttachment(); 
    attachment.setPath(fileName);
    attachment.setDisposition(EmailAttachment.ATTACHMENT); 
    attachment.setDescription(MimeUtility.encodeWord("附件","UTF-8",null)); 
    attachment.setName(MimeUtility.encodeWord("视频内容报备${date.format('yyyyMMdd')}" + ext,"UTF-8",null)); 

    email.attach(attachment); 

    // send the email 
    email.send(); 
}


def buildPackage(def date, def fileName) {

    def wb = new HSSFWorkbook();
    def createHelper = wb.getCreationHelper();
    def sheet = wb.createSheet("sheet1");

    short rowIdx = 0

    // create style
    def style = wb.createCellStyle();
    def font = wb.createFont();
    font.setBoldweight(Font.BOLDWEIGHT_BOLD);
    style.setFont(font);
    style.setAlignment(CellStyle.ALIGN_CENTER);
    
    def style2 = wb.createCellStyle();
    style2.setAlignment(CellStyle.ALIGN_CENTER);

    // first row - title
    def titleCell = sheet.createRow(rowIdx).createCell(0)
    titleCell.setCellValue("视频内容报备(${new Date().format('yyyy/MM/dd')})")
    titleCell.setCellStyle(style)

    def row1 = sheet.createRow(++rowIdx)
    def idxCell = 0
    
    // second row - head
    titleCell = row1.createCell(idxCell++); titleCell.setCellValue('序号'); titleCell.setCellStyle(style);
    titleCell = row1.createCell(idxCell++); titleCell.setCellValue('标题'); titleCell.setCellStyle(style);
    
    sheet.addMergedRegion(new CellRangeAddress(
        0, //first row (0-based)
        0, //last row  (0-based)
        0, //first column (0-based)
        idxCell-1  //last column  (0-based)
    ));
    
    
    // 
    def today = date.format('yyyy年MM月dd日')

    getVideoList(date).eachWithIndex { item,idx ->
        // data row
        def cell
        def row = sheet.createRow(++rowIdx)
        idxCell = 0
        
        cell = row.createCell(idxCell++); cell.setCellValue(idx+1); cell.setCellStyle(style2);// index
        cell = row.createCell(idxCell++); cell.setCellValue(item.title);     // title
    }

    def fos = new FileOutputStream(fileName + '.xls');
    wb.write(fos);
    fos.close();
    
    // build zip file
    ZipOutputStream zipFile = new ZipOutputStream(new FileOutputStream(fileName + '.zip'))
    zipFile.putNextEntry(new ZipEntry(fileName + '.xls'))
    def buffer = new byte[1024]
    def file = new File(fileName + '.xls');
    
    InputStream is = new BufferedInputStream(new FileInputStream(file));
    while ((readLen = is.read(buffer)) != -1) {
        zipFile.write(buffer, 0, readLen);
    }
    is.close();

    zipFile.closeEntry()
    zipFile.close()
    
    new File(fileName + '.xls').delete()
}



try {

    def date = new Date();
    def file = "${date.format('yyyyMMdd')}"
    buildPackage(date, file);
    sendPackage(date, file + '.zip', [
            ["king3g@139.com", "王俊"],
            ["jinxia@temobi.com", "靳霞"]
        ])
    new File(file + '.zip').delete()
    
} catch (Exception ex) {
    ex.printStackTrace();
}
