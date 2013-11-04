import java.security.MessageDigest

class Utils {

    static HEX_DIGITS = "0123456789abcdef"

    static encodeAsHex(def theTarget) {
    
        if (theTarget == null) {
            return null
        }
        else {
            def result = new StringBuffer()
            theTarget.each() {
                result << HEX_DIGITS[(it & 0xF0) >> 4]
                result << HEX_DIGITS[(it & 0x0F)]
            }
            return result.toString()
        }
    }
    
    static encodeAsMD5Hex(def theTarget) {
        encodeAsHex(encodeAsMD5(theTarget))
    }
    
    static encodeAsMD5(def theTarget){
        if (theTarget == null) {
            return null
        }
        else {
            def md = MessageDigest.getInstance("MD5")
            return md.digest(theTarget.toString().getBytes())
        }
    }
    
    static encodeAsSHA1Hex(def theTarget) {
        encodeAsHex(encodeAsSHA1(theTarget))
    }
    
    static encodeAsSHA1(def theTarget){
        if (theTarget == null) {
            return null
        }
        else {
            def md = MessageDigest.getInstance("SHA1")
            return md.digest(theTarget.toString().getBytes())
        }
    }
    
    static void mkdirs4file(filePath) {
        int idx = filePath.lastIndexOf('/')
        new File(filePath.substring(0,idx)).mkdirs()
    }
    
}