
var cbotGlobal={};
cbotGlobal.NV="unknown";
cbotGlobal.lastUUID=cbotGlobal.NV;
cbotGlobal.app=cbotGlobal.NV;
cbotGlobal.inst=cbotGlobal.NV;
cbotGlobal.firstLoaded=true;



function calcDelta(de,a) {
  if (de > a) {
    return -1
  }
  else if (de < a) {
    return 1;
  }
  else {
    return 0;
  }
}

function moveMark(x,y) {
  var mark=document.getElementById("mark");
  var markX=mark.style.left;
  var markY=mark.style.top;
  markX=parseInt(markX,10);
  markY=parseInt(markY,10);
  var deltaX=calcDelta(markX,x),deltaY=calcDelta(markY,y);
  if ((deltaX!=0) || (deltaY!=0)) {
    var moveIt=function() {
      if (markX!=x || markY!=y) {
        mark.style.left=markX+"px";
        mark.style.top=markY+"px";
        if (deltaX!=0 && markX!=x)
          markX+=deltaX;
        if (deltaY!=0 && markY!=y)
          markY+=deltaY;
        setTimeout(moveIt,1);
      }
    }
    moveIt();
  }
}

function setMarkImg(id) {
  jQuery("#mark > div").hide();
  jQuery(id).show();
}

function updateSemaphore(stop,awaiting) {
  if (stop)
    setMarkImg("#stopimg");
  else if (awaiting)
    setMarkImg("#waitimg");
  else
    setMarkImg("#runimg");
}

function startAnimation() {
  if (cbotGlobal.app!==cbotGlobal.NV && cbotGlobal.inst!==cbotGlobal.NV) {
    jQuery.ajax({
      url: "/apps/"+cbotGlobal.app+"/"+cbotGlobal.inst,
      data: {cmd: "current-pos",
             uuid: cbotGlobal.lastUUID,
             timeout: "20000"},
      success: handler});
  }
}

function setIt(key,result) {
  var e=document.getElementById(key);
  e.innerHTML=result[key];
}

function clearStats() {
/*
  for (i=0; i<10; i++) {
    var state=document.getElementById("status.state."+i);
    var result=document.getElementById("status.result."+i);
    var whenx=document.getElementById("status.when."+i);
    var delta=document.getElementById("status.delta."+i);
    state.innerText="";
    result.innerText="";
    whenx.innerText="";
    delta.innerText="";
  }
  */
}

cbotGlobal.etiqueta=[];
cbotGlobal.etiqueta[0]="state";
cbotGlobal.etiqueta[1]="result";
cbotGlobal.etiqueta[2]="when";
cbotGlobal.etiqueta[3]="delta-micro";

function handler(resultx) {
  var thumbUp=$("#thumb-up");
  var thumbDn=$("#thumb-down");
  var result=eval("("+resultx+")");
  if (result.app === cbotGlobal.app && result.inst === cbotGlobal.inst) {
    cbotGlobal.lastUUID=result.uuid;
    var xx=parseInt(result.x,10);
    var yy=parseInt(result.y,10);  
    updateSemaphore(result["stop?"],result["awaiting?"]); 

    thumbUp.hide();
    thumbDn.hide();  
    if (result.status === "bad") {
      thumbDn.show();
    }
    else {
      thumbUp.show();
    }
    var resultIndex=0;
    var x=jQuery("table.status").find("tr").each(function (i) {
      if (i>0) {
        $(this).find("td").each(function (j) {
        //if (resultIndex<result["stats"]["info"].length
          if (resultIndex<result.stats.info.length) { 
            $(this).text(result.stats.info[resultIndex][cbotGlobal.etiqueta[j]]);
          }
          else {
            $(this).text("");
          }   
        });
        resultIndex+=1;
      }
    });

    moveMark(xx,yy);
    if (!result["stop?"]) {
      startAnimation();
    }
  }
}

function instanceChange() {
  cbotGlobal.inst=jQuery("#instances option:selected").text();
  startAnimation();
}

function fillSelect(valStr,tagLabel,globalName,changeFunc,nextFunc) {
  var json=eval("("+valStr+")");
  var combo=jQuery(tagLabel).empty();
  var selected=cbotGlobal.NV;

  for (i=0; i<json.length; i+=1) {
    if (i===0) {
      combo.prepend('<option value="'+json[i]+'" selected="selected">'+json[i]+'</option>');
      selected=json[i];
    }
    else {
      combo.prepend('<option value="'+json[i]+'">'+json[i]+'</option>');
    }
  }

  if (nextFunc) {
    nextFunc(selected);
  }

  jQuery(tagLabel).change(changeFunc);
  cbotGlobal[globalName]=selected;
}

function fillInstances(instancesStr) {
  fillSelect(instancesStr,"#instances","inst",instanceChange,null );
  if (cbotGlobal.firstLoaded) {
    cbotGlobal.firstLoaded=false;
    applicationChange();
    instanceChange();
  }
  else {
    startAnimation();
  }
}

function applicationChange() {
  var app=jQuery("#applications option:selected").text();
  jQuery.ajax({
    url: "/apps/"+app,
    success: function (instancesStr) {
      fillInstances(instancesStr);
      //startAnimation();
    }
  });
  cbotGlobal.app=app;
  cbotGlobal.inst=cbotGlobal.NV;
  var imagen=jQuery("#states > img").load(function () {
    $(this).fadeIn(); 
    //alert("height="+$(this).height());
  });
  
  imagen.attr('src','/cbotimg/'+app);
}

function fillApplications(appsStr) {
  fillSelect(appsStr,"#applications","app",applicationChange,function (selected) {
    jQuery.ajax({
      url: "/apps/"+selected,
      success: fillInstances});
  });
}

jQuery(document).ready(function() {
  jQuery("#start-button").click(function () {
    if (cbotGlobal.app!==cbotGlobal.NV && cbotGlobal.inst!==cbotGlobal.NV) {
      startInstance(cbotGlobal.inst);
    }
    else {
      alert("first you must select application and instance !");
    }
  });
  jQuery("#stop-button").click(function () {
    if (cbotGlobal.app!==cbotGlobal.NV && cbotGlobal.inst!==cbotGlobal.NV) {
      stopInstance(cbotGlobal.inst);
    } 
    else {
      alert("first you must select application and instance !");
    }
  });
  jQuery("#resume-button").click(function () {
    if (cbotGlobal.app!==cbotGlobal.NV && cbotGlobal.inst!==cbotGlobal.NV) {
      resumeInstance(cbotGlobal.inst);
    }
    else {
      alert("first you must select application and instance !");
    }
  });
//  jQuery("#mark").click(function () {startAnimation()});
/*
  jQuery("#mark").on("mousedown", function (m) {
    jQuery(this).startDrag(m,jQuery(this).getNinth(m));
  }).on("mousemove","drag").on("mouseup","stopDrag");
  */
  var apps=jQuery("#applications"); 

  apps.empty();   
  jQuery.ajax({
    url: "/apps",
    success: fillApplications});
});


function startInstance(instName) {
  jQuery.ajax({
    url: "/apps/"+cbotGlobal.app+"/"+cbotGlobal.inst,
    data: {"cmd":"start"},
    success: function() {
      startAnimation();  
    }
  });
  
}

function stopInstance(instName) {
  jQuery.ajax({
    url: "/apps/"+cbotGlobal.app+"/"+cbotGlobal.inst,
    data: {"cmd":"stop"},
    success: function() {
      startAnimation();  
    }
  });
}

function resumeInstance(instName) {
//  var msg=document.getElementById("resume-msg").value;
  var msg=jQuery("#resume-msg").val();
  jQuery.ajax({
    url: "/apps/"+cbotGlobal.app+"/"+cbotGlobal.inst,
    data: {"cmd":"resume",
           "msg":msg},
    success: function() {
      startAnimation();
    }
  });
}

