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

function startAnimation() {
  var client = new XMLHttpRequest();
  client.onreadystatechange = handler;
  client.open("GET", "/apps/WEB/primaria?cmd=current-pos"); 
  client.send();
}

function handler() {
  if (this.readyState == 4 && this.status == 200) {
    //var infor=document.getElementById("info");
    var result=eval("("+this.responseText+")");
    var xx=parseInt(result.x);
    var yy=parseInt(result["y"]);    
    //infor.innerHTML=new Date();
    moveMark(xx,yy);
    setTimeout(startAnimation,500);
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
      //alert("Instance:"+instName+" started!");  
      var semG=document.getElementById("sem-green");
      var semY=document.getElementById("sem-yellow");
      var semR=document.getElementById("sem-red");
      semY.style.display="none";
      semR.style.display="none";
      semG.style.display="inherit";
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
      //alert("Instance:"+instName+ " stopped!" );  
      var semG=document.getElementById("sem-green");
      var semY=document.getElementById("sem-yellow");
      var semR=document.getElementById("sem-red");
      semG.style.display="none";
      semY.style.display="none";
      semR.style.display="inherit";} 
  }
  client.open("GET", "/apps/WEB/primaria?cmd=stop"); 
  client.send();  
}


