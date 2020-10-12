// Testing call-graph with callback

var https = {};
https.run = function (cb) {
    cb("hello, world!");
};
https.run2 = function (cb) {
    cb(unknown());
};

https.run((r) => {});   // This callback will appear in the call-graph
https.run2((r) => {});  // But this callback won't -- since "unknown" is unknown