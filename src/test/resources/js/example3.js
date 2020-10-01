var child_process_1 = require("child_process");
var SfdxUtils = function $SfdxUtils$() {
};
SfdxUtils.execSfdx = function $SfdxUtils$execSfdx$($command$$, $targetusername$$) {
  return "undefined" != typeof $targetusername$$ ? child_process_1.execSync("sfdx " + $command$$ + " --targetusername " + $targetusername$$).toString() : child_process_1.execSync("sfdx " + $command$$).toString();
};
