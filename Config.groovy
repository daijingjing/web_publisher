
class Config {
    static def setting = [:]
    
    static {
        Config.readConfig()
    }
    
    static String configFile = 'config.properties'

    static def readConfig() {
        new File(configFile).readLines().toList().each { line ->
            if (line ==~ /^\s#.*/){
                // comment line
            } else {
                int splitIdx = line.indexOf('=')
                if (splitIdx > 0){
                    def k = line.substring(0, splitIdx)
                    def v = line.substring(splitIdx + 1)
                    Config.setting[k] = v
                }
            }
        }
    }
    
    static def writeConfig() {
    }
}