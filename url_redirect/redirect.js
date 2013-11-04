var http = require('http'),
    url = require('url'),
    mysql = require('mysql'),
    fs = require('fs');

//
// short url tools
//
var digits = ['0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z','A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P','Q','R','S','T','U','V','W','X','Y','Z','@','#'];

function short_url_encode(dec, shift) {
    var buf = '', mask = (1 << shift) - 1;
    do {
        buf += digits[dec & mask];
        dec >>>= shift;
    } while (dec != 0);
    return buf.split("").reverse().join("");
}

function short_url_decode(s, shift) {
    var Num = 0;
    for (var i = s.length; i > 0; i--) {
        var j = digits.indexOf(s[i - 1]);
        Num += j * Math.pow(1 << shift, s.length - i);
    }
    return Num;
}


//
// mysql connect
//
var pool = mysql.createPool({
    host: 'localhost',
    user: 'root',  
    password: '1q2w3e',
    insecureAuth: true  
});
var pool2 = mysql.createPool({
    host: '192.168.2.34',
    user: 'dbm_mvn',  
    password: 'spn@wxcs',
    insecureAuth: true  
});

var cache = [];
var max_chache = 5000;

//
// server
//
http.createServer(function(request, response){

    if (request.url.match(/^\/s\/.+/)) {

        var urlid = short_url_decode(request.url.substr(3,Math.min(8, request.url.length)), 6);
        
        // test cache
        for(var i = 0; i < cache.length; i++) {
            if (cache[i].urlid == urlid) {
                // hit cache

                var reurl = cache[i].reurl;
                response.writeHead(302, {'Location': reurl});
                response.end();
                console.log(reurl + '[cached]');    

                return;
            }
        }
        
        pool2.getConnection(function(err, otherClient) {
            if (err) {
                response.writeHead(500, {'Content-Type':'text/plain'});
                response.write('500 Server Error - pool2.getConnection');
                response.end();
                return;
            }
            
            otherClient.query("SELECT REPLACE(SUBSTR(b.sj_img,51),'.jpg','') vid FROM (SELECT SUBSTR(target_url,49,36) as uuid FROM `ydview`.`short_url` WHERE id = " + otherClient.escape(urlid) + " ) a INNER JOIN `ydview`.`sj_content` b ON a.uuid = b.sj_contentid", function(err,results,fields) {
            
                if (err) {
                    response.writeHead(500, {'Content-Type':'text/plain'});
                    response.write('500 Server Error - otherClient.query');
                    response.end();
                    return;
                }
                
                if (results.length == 0) {
                    response.writeHead(404, {'Content-Type':'text/plain'});
                    response.write('404 Not Found');
                    response.end();
                    return;
                }
                
                pool.getConnection(function(err, client) {
                    if (err) {
                        response.writeHead(500, {'Content-Type':'text/plain'});
                        response.write('500 Server Error - pool.getConnection');
                        response.end();
                        return;
                    }
                    
                    client.query('SELECT uuid,title,published FROM video.video_resource WHERE vid = ' + client.escape(results[0].vid), function(err,results,fields) {
                        if (err) {
                            response.writeHead(500, {'Content-Type':'text/plain'});
                            response.write('500 Server Error - client.query');
                            response.end();
                            return;
                        }
                        
                        if (results.length > 0) {
                            var reurl = '/v/'+results[0].uuid+'.html';
                            response.writeHead(302, {'Location': reurl});
                            response.end();
                            console.log(reurl);
                            
                            // put cache
                            cache.push({'urlid':urlid, 'reurl':reurl})
                            // clear cache
                            if (cache.length > max_chache) {
                                for(var ci=0; ci < 1000; ci++) {
                                    chache.pop();
                                }
                            }
                            
                            
                        }else{
                            response.writeHead(404, {'Content-Type':'text/plain'});
                            response.write('404 Not Found');
                            response.end();
                        }
                    });
                    
                    client.end();
                });
            });
            
            otherClient.end();
        });

    } else if (request.url.match(/^\/log\/.+/)) {
        try {
            // console.log(request);
            var ctx = request.url.substr(5);
            ctx = ctx.split('/');
            var date = new Date().format('yyyy-mm-dd HH:MM:ss');
            var log;
            if (ctx.length > 1) {
                log = decodeURI(ctx[0]) + '|' + decodeURI(ctx[1]) + '|' + decodeURI(ctx[2]) + '|' + request.headers['x-real-ip'];
            } else {
                log = decodeURI(ctx[0]) + '|' + request.headers['x-real-ip'];
            }
            log = log.replace('undefined','');
            log = date + ' INFO  com.temobi.index.action.LogsAction - ' + log;
            fs.appendFile('/wap1/log/testview/logFile.' + new Date().format('yyyy-mm-dd') + '.log', log + '\n', function (err) { if (err) console.error(err); console.log(log); });
            
        }catch(e){
            console.log(e);
        }
        response.writeHead(200, {'Content-Type':'text/plain'});
        response.end();
    } else {
        response.writeHead(405, {'Content-Type':'text/plain'});
        response.write('405 Method Not Allowed');
        response.end();
    }

}).listen(8124);

console.log('server is running at 8124...');





//
// date format
//

var dateFormat = function () {
    var token = /d{1,4}|m{1,4}|yy(?:yy)?|([HhMsTt])\1?|[LloSZ]|"[^"]*"|'[^']*'/g,
        timezone = /\b(?:[PMCEA][SDP]T|(?:Pacific|Mountain|Central|Eastern|Atlantic) (?:Standard|Daylight|Prevailing) Time|(?:GMT|UTC)(?:[-+]\d{4})?)\b/g,
        timezoneClip = /[^-+\dA-Z]/g,
        pad = function (val, len) {
            val = String(val);
            len = len || 2;
            while (val.length < len) val = "0" + val;
            return val;
        };

    // Regexes and supporting functions are cached through closure
    return function (date, mask, utc) {
        var dF = dateFormat;

        // You can't provide utc if you skip other args (use the "UTC:" mask prefix)
        if (arguments.length == 1 && Object.prototype.toString.call(date) == "[object String]" && !/\d/.test(date)) {
            mask = date;
            date = undefined;
        }

        // Passing date through Date applies Date.parse, if necessary
        date = date ? new Date(date) : new Date;
        if (isNaN(date)) throw SyntaxError("invalid date");

        mask = String(dF.masks[mask] || mask || dF.masks["default"]);

        // Allow setting the utc argument via the mask
        if (mask.slice(0, 4) == "UTC:") {
            mask = mask.slice(4);
            utc = true;
        }

        var _ = utc ? "getUTC" : "get",
                d = date[_ + "Date"](),
                D = date[_ + "Day"](),
                m = date[_ + "Month"](),
                y = date[_ + "FullYear"](),
                H = date[_ + "Hours"](),
                M = date[_ + "Minutes"](),
                s = date[_ + "Seconds"](),
                L = date[_ + "Milliseconds"](),
                o = utc ? 0 : date.getTimezoneOffset(),
                flags = {
                        d:    d,
                        dd:   pad(d),
                        ddd:  dF.i18n.dayNames[D],
                        dddd: dF.i18n.dayNames[D + 7],
                        m:    m + 1,
                        mm:   pad(m + 1),
                        mmm:  dF.i18n.monthNames[m],
                        mmmm: dF.i18n.monthNames[m + 12],
                        yy:   String(y).slice(2),
                        yyyy: y,
                        h:    H % 12 || 12,
                        hh:   pad(H % 12 || 12),
                        H:    H,
                        HH:   pad(H),
                        M:    M,
                        MM:   pad(M),
                        s:    s,
                        ss:   pad(s),
                        l:    pad(L, 3),
                        L:    pad(L > 99 ? Math.round(L / 10) : L),
                        t:    H < 12 ? "a"  : "p",
                        tt:   H < 12 ? "am" : "pm",
                        T:    H < 12 ? "A"  : "P",
                        TT:   H < 12 ? "AM" : "PM",
                        Z:    utc ? "UTC" : (String(date).match(timezone) || [""]).pop().replace(timezoneClip, ""),
                        o:    (o > 0 ? "-" : "+") + pad(Math.floor(Math.abs(o) / 60) * 100 + Math.abs(o) % 60, 4),
                        S:    ["th", "st", "nd", "rd"][d % 10 > 3 ? 0 : (d % 100 - d % 10 != 10) * d % 10]
                };

        return mask.replace(token, function ($0) {
            return $0 in flags ? flags[$0] : $0.slice(1, $0.length - 1);
        });
    };
}();
 
// Some common format strings
dateFormat.masks = {
    "default":      "ddd mmm dd yyyy HH:MM:ss",
    shortDate:      "m/d/yy",
    mediumDate:     "mmm d, yyyy",
    longDate:       "mmmm d, yyyy",
    fullDate:       "dddd, mmmm d, yyyy",
    shortTime:      "h:MM TT",
    mediumTime:     "h:MM:ss TT",
    longTime:       "h:MM:ss TT Z",
    isoDate:        "yyyy-mm-dd",
    isoTime:        "HH:MM:ss",
    isoDateTime:    "yyyy-mm-dd'T'HH:MM:ss",
    isoUtcDateTime: "UTC:yyyy-mm-dd'T'HH:MM:ss'Z'"
};
 
// Internationalization strings
dateFormat.i18n = {
    dayNames: [
           "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat",
           "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
    ],
    monthNames: [
           "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
           "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"
    ]
};
 
// For convenience...
Date.prototype.format = function (mask, utc) {
    return dateFormat(this, mask, utc);
};
