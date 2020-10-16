var child_process_1 = require("child_process");

var SfdxUtils = {};
SfdxUtils.execSfdx = function ($command$$, $targetusername$$) {
  return "undefined" != typeof $targetusername$$ ? child_process_1.execSync("sfdx " + $command$$ + " --targetusername " + $targetusername$$).toString() : child_process_1.execSync("sfdx " + $command$$).toString();
};

module.exports = SfdxUtils;module.exports();
module.exports.execSfdx();