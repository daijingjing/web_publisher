import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;


class ImportExcel {

    // 视频文件夹
    String basePath = './';
    Db db = null;

    ImportExcel() {
        db = new Db()
        if (Config.setting.video_path) {
            basePath = Config.setting.video_resource_path
        }
    }

    ImportExcel(host, name, user, pass) {
        db = new Db(host, name, user, pass)
        if (Config.setting.video_path) {
            basePath = Config.setting.video_resource_path
        }
    }

    def import2db(def datas, def publishDate) {
        def count = 0
        
        datas.each{ data ->
            data.published = publishDate
            
            // 判断源文件是否存在，存在就入库，不存在就放弃
            if (new File("$basePath/${publishDate.format('yyyyMMdd')}/${data.vid}.jpg").exists() &&
                new File("$basePath/${publishDate.format('yyyyMMdd')}/${data.vid}_11_jk.3gp").exists() &&
                new File("$basePath/${publishDate.format('yyyyMMdd')}/${data.vid}_13_jm.3gp").exists()) {
                if (db.addVideo(data))
                    count++;
            }
        }
        
        return count
    }

    def extractKeywords(String desc) {
        def matcher = ( desc =~ /\[(.*?)\]/ )
        if (matcher && matcher[0]) {
            return matcher[0][1].split(',|\\||\\+').collect{it.replace('*','').trim()}
        }
        else {
            return ''
        }
    }

    def filterDesc(String desc) {
        def matcher = ( desc =~ /(\[.*?\])/ )
        return matcher.replaceAll('').replace('内容由华数手机视频提供。','')
    }

    def parseExcelFile(String fileName) {
        FileInputStream fis = new FileInputStream(new File(fileName));

        def workbook = WorkbookFactory.create(fis);
        def evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        def formatter = new DataFormatter(true);

        def datas = []

        Sheet sheet = null;
        Row row = null;
        int lastRowNum = 0;
        int maxRowWidth = 0;

        int numSheets = workbook.getNumberOfSheets();

        for(int sheetIdx = 0; sheetIdx < numSheets; sheetIdx++) {

            sheet = workbook.getSheetAt(sheetIdx);
            if(sheet.getPhysicalNumberOfRows() > 0) {

                lastRowNum = sheet.getLastRowNum();
                for(int j = 0; j <= lastRowNum; j++) {
                    row = sheet.getRow(j);
                    
                    Cell cell = null;
                    int lastCellNum = 0;
                    def line = []

                    if(row != null) {

                        lastCellNum = row.getLastCellNum();
                        for(int i = 0; i <= lastCellNum; i++) {
                            cell = row.getCell(i);
                            if(cell == null) {
                                line.add("");
                            } else {
                                if(cell.getCellType() != Cell.CELL_TYPE_FORMULA) {
                                    line.add(formatter.formatCellValue(cell));
                                } else {
                                    line.add(formatter.formatCellValue(cell, evaluator));
                                }
                            }
                        }
                        if(lastCellNum > maxRowWidth) {
                            maxRowWidth = lastCellNum;
                        }
                    }
                    
                    if (line.size() > 0 && line[0] ==~ /^\d+.*/) {
                        datas.add([
                            'channel':  line[3],
                            'uuid':     UUID.randomUUID().toString().replace('-',''),
                            'vid':      line[0],
                            'title':    line[1],
                            'keywords': extractKeywords(line[2]),
                            'desc':     filterDesc(line[2])
                            ]);
                    }
                }
            }
        }

        return datas
    }


    def importAll() {

        def count = 0
        
        new File(basePath).listFiles( [accept:{ file -> file.isDirectory() && file.name ==~ /^[0-9]{8}.*$/ }] as FileFilter ).toList()*.name.each { dir ->
            def excelFileName = this.basePath + dir + '/' + dir + '.xls'
            println "Import excel file ${excelFileName} ..."
            if (new File(excelFileName).exists()) {
                def videoList = parseExcelFile( excelFileName );
                count += import2db(videoList, Date.parse('yyyyMMdd', dir.size() > 8 ? dir.substring(0,8) : dir));
            }
        }
      
        println "Import count:" + count
    }

    def importDate(String dir) {

        def count = 0
        def excelFileName = this.basePath + dir + '/' + dir + '.xls'

        if (new File(excelFileName).exists()) {
            println "Import excel file ${excelFileName} ..."
            def videoList = parseExcelFile( excelFileName );
            count += import2db(videoList, Date.parse('yyyyMMdd', dir.size() > 8 ? dir.substring(0,8) : dir));

            def fw= new FileWriter("video_list.$dir");
            videoList.each{ v ->
                fw.write(v.vid);
                fw.write('\n');
            }
            fw.close();
        }
        
        println "Import count:" + count
    }


    def importToday() {
        importDate(new Date().format('yyyyMMdd'));
    }
    
    //
    // entery
    //
    static void main(String[] args) {

        def cli = new CliBuilder()  
        cli.h( longOpt: 'help', required: false, '显示当前帮助信息' )  
        cli.d( longOpt: 'dbhost', argName: 'dbhost', required: false, args: 1, 'MySQL服务器名或IP地址' )  
        cli.n( longOpt: 'dbname', argName: 'dbname', required: false, args: 1, 'MySQL数据库名' )  
        cli.u( longOpt: 'dbuser', argName: 'dbuser', required: false, args: 1, 'MySQL用户名' )  
        cli.p( longOpt: 'dbpass', argName: 'dbpass', required: false, args: 1, 'MySQL用户密码' )  
        cli.b( longOpt: 'base-path', argName: 'basepath', required: false, args: 1, '视频路径' )  
        cli.t( longOpt: 'date', argName: 'yyyyMMdd', required: false, args: 1, '导入指定日期的视频列表' )  
        cli.a( longOpt: 'all', required: false, args: 0, '导入所有符合要求的视频列表' )  
          
        def opt = cli.parse(args)  
        if (!opt) return;
        if (opt.h) {  
            cli.usage();
            return  
        }
        
        def instance = (opt.d && opt.n && opt.u && opt.p) ? new ImportExcel(opt.d,opt.n,opt.u,opt.p) : new ImportExcel()

        if (opt.b) instance.basePath = opt.b;
    
        if (opt.a){
            instance.importAll()
        } else if (opt.date) {
            instance.importDate(opt.date)
        } else {
            instance.importToday()
        }
    
    }

}

