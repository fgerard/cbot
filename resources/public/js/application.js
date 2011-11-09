// jsApplication Viewer
// InterWare de Mexico, S.A. de C.V.
// Felipe Gerard

var cbotGlobal={};
cbotGlobal.NV="unknown";
cbotGlobal.app=cbotGlobal.NV;
cbotGlobal.inst=cbotGlobal.NV;

function localUUID() {
  return Raphael.createUUID();
}

function saveStates() {
  jQuery.ajax({
    type: "POST",
    url: "/store/"+cbotGlobal.app,
    dataType: "json",
    data:{"conf":cbotGlobal.conf},
    success: function(result) {
      //$("#index").submit();
      location.href="/index.html";
    }
  });
}

////////////////////////////////////////////////////////////

function extractParams(tagId) {
  var pars={};
  pars["-"]="-";
  var esKey=true;
  var kv=[];
  var i=0;
  $(tagId).find("input").each(function() {
    if (i>0) {
      kv.push($(this).val());
      if (!esKey) {
        pars[kv[0]]=kv[1];
        kv=[];
      }
      esKey=!esKey;
    }
    i=++i % 3;
  });
  return pars;
}

jQuery(document).ready(function() {
  jQuery("#add-p-btn").click(function() {
    var uuid=localUUID();
    jQuery("#parameters").append('<tr><td><input type="button" id='+uuid+' class="button" value="Del"/></td><td><input type=\"text\"/></td><td><input type=\"text\"/></td></tr>');
    jQuery("#"+uuid).click(function () {
      var tr=$("#"+uuid).parent().parent().remove();
    });
  });
  jQuery("#add-i-btn").click(function() {
    var uuid=localUUID();
    var instUUID=localUUID();
    var addUUID=localUUID(); 
    jQuery("#instances").append('<tr><td><input type="button" id='+uuid+' class="button" value="Del"/></td><td><input class=\"instance\" name=\"'+instUUID+'\" type=\"text\"/></td><td><table><tr><td><table id='+instUUID+' border="1" cellspacing="5" width=\"500pt\"><tr><th width="10%"></th><th width="20%">Name</th><th width="70%">Value of parameter</th></tr></table></td></tr><tr><td><input type="button" id='+addUUID+' class="button" value="Add parameter"/></td></tr></table></td></tr>');
    jQuery("#"+uuid).click(function () {
      var tr=$("#"+uuid).parent().parent().remove();
    });
    jQuery("#"+addUUID).click(function() {
      var delUUID=localUUID(); 
      jQuery("#"+instUUID).append('<tr><td><input type="button" id='+delUUID+' class="button" value="Del"/></td><td><input type=\"text\"/></td><td><input type=\"text\"/></td></tr>');
      jQuery("#"+delUUID).click(function () {
        var tr=$("#"+delUUID).parent().parent().remove();
      });
    });
  });
  jQuery("#create-app").click(function(){
    var name=$("#app-name").val();
    var interStateDelay=$("#interstate-delay").val();
    var statsCacheLen=$("#stats-cache-len").val();
    var appPars=extractParams("#parameters");
    var instances={};   
    $("#instances").find(".instance").each(function() {
      var instName=$(this).val();
      instances[instName]={};
      instances[instName]["param-map"]=extractParams("#"+this.name);
    });
    var conf={};
    conf["interstate-delay"]=interStateDelay;
    conf["stats-cache-len"]=statsCacheLen;
    conf["parameters"]=appPars;
    conf["instances"]=instances;
    conf.states=[];
    conf.states.push({"flow": {"y":100, "x":100, "connect": []}, "key": ":inicio", "conf-map":{"opr": "sleep-opr", "conf": {"delta": "3000"}}});
//{:flow {:y "110", :x "99", :connect [":pagina"]}, :key ":inicio", :conf-map {:opr "sleep-opr", :conf {:delta "3000"}}}
    cbotGlobal.app=name;
    cbotGlobal.conf=conf;
    saveStates();
  });
});
