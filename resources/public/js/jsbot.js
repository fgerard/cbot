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
  markX=parseInt(markX);
  markY=parseInt(markY);
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


function setGreen() {
  var semG=document.getElementById("sem-green");
  var semY=document.getElementById("sem-yellow");
  var semR=document.getElementById("sem-red");
  semY.style.display="none";
  semR.style.display="none";
  semG.style.display="inherit";

  var imgStop=document.getElementById("stopimg");
  var imgRun=document.getElementById("runimg");
  var imgWait=document.getElementById("waitimg");
  imgWait.style.visibility="hidden";
  imgRun.style.visibility="visible";
  imgStop.style.visibility="hidden";
}

function setRed() {
  var semG=document.getElementById("sem-green");
  var semY=document.getElementById("sem-yellow");
  var semR=document.getElementById("sem-red");
  semY.style.display="none";
  semG.style.display="none";
  semR.style.display="inherit";

  var imgStop=document.getElementById("stopimg");
  var imgRun=document.getElementById("runimg");
  var imgWait=document.getElementById("waitimg");
  imgWait.style.visibility="hidden";
  imgStop.style.visibility="visible";
  imgRun.style.visibility="hidden";
}
function setYellow() {
  var semG=document.getElementById("sem-green");
  var semY=document.getElementById("sem-yellow");
  var semR=document.getElementById("sem-red");
  semG.style.display="none";
  semR.style.display="none";
  semY.style.display="inherit";

  var imgStop=document.getElementById("stopimg");
  var imgRun=document.getElementById("runimg");
  var imgWait=document.getElementById("waitimg");
  imgWait.style.visibility="visible";
  imgRun.style.visibility="hidden";
  imgStop.style.visibility="hidden";

}

function updateSemaphore(stop,awaiting) {
  if (stop)
    setRed();
  else if (awaiting)
    setYellow();
  else
    setGreen();
}

var lastUUID="unknown";

function startAnimation() {
  var client = new XMLHttpRequest();
  client.onreadystatechange = handler;
  client.open("GET", "/apps/WEB/primaria?cmd=current-pos&uuid="+lastUUID+"&timeout=20000"); 
  client.send();
}

function setIt(key,result) {
  var e=document.getElementById(key);
  e.innerHTML=result[key];
}

function setStats(info) {
  for (i=0; i<info.length; i++) {
    var state=document.getElementById("stats.state."+i);
    var result=document.getElementById("stats.result."+i);
    var delta=document.getElementById("stats.delta."+i);
    state.innerHTML=info[i].state;
    result.innerHTML=info[i].when;
    delta.innerHTML=info[i]["delta-micro"];
  }
}

function handler() {
  if (this.readyState == 4 && this.status == 200) {
    var result=eval("("+this.responseText+")");
    lastUUID=result.uuid;
    var xx=parseInt(result.x);
    var yy=parseInt(result["y"]);  
    updateSemaphore(result["stop?"],result["awaiting?"]); 
    setIt("stop?",result);
    setIt("current",result);
    setIt("awaiting?",result);
    setIt("state-count",result);
    setIt("last-ended",result); 
    setStats(result.stats.info);
    moveMark(xx,yy);
    //alert(result.stats.info[0].state);
    //setTimeout(startAnimation,500);
    startAnimation();
  } else if (this.readyState == 4 && this.status != 200) {
    //var infor=document.getElementById("info");
    //infor.innerHTML="problems in page /apps/WEB/primaria?cmd=current-pos";
  }
}

function move(cmbo) {
  var val=cmbo[cmbo.selectedIndex].value, coordX=dataX[val], coordY=dataY[val];
  //alert(coordX+","+coordY);
  //moveMark(coordX,coordY);
}

startAnimation();


function startInstance(instName) {
  var client = new XMLHttpRequest();
  client.onreadystatechange = function() {
    if (this.readyState == 4 && this.status == 200) {
      var result=this.responseText;
      //alert(result);
      //setGreen();
    } 
  }
  client.open("GET", "/apps/WEB/primaria?cmd=start"); 
  client.send();
}

function stopInstance(instName) {
  var client = new XMLHttpRequest();
  client.onreadystatechange = function() {
    if (this.readyState == 4 && this.status == 200) {
      var result=this.responseText;
      //setRed();
    } 
  }
  client.open("GET", "/apps/WEB/primaria?cmd=stop"); 
  client.send();  
}


