'use strict';

var app = require('express')(),
    bodyParser = require('body-parser'),
    http = require('http').Server(app),
    io = require('socket.io')(http),
    sqlite3 = require('sqlite3'),
    fs=require('fs');

 // middleware

app.use(bodyParser.json());

var rooms = {};
rooms["public-world"]={room:'public-world', questions:{}, members:[], membersIndex:{}, moderatorPwd: 'scenemax3d', desc:'General international free room available for everyone'};
//rooms["ezcode"]={room:'ezcode', questions:{}, members:[], membersIndex:{}, pwd: '123d', moderatorPwd: 'smartmakers', desc:'Room for Mamreem company courses'};

io.on('connection', function(socket){

//  socket.on('connect', function(){
//
//  });

  socket.on('disconnect', function(){

    Object.keys(rooms).forEach(function (key) {

        var room = rooms[key];
        if(room.membersIndex[socket.id]) {
            leaveRoom(key,socket);
            io.to(key).emit('user-left', {room:key, members:room.members});
        }
        // use val
    });


  });

  socket.on('create-room', (data) => {

      var room = rooms[data.room];
      if(room) {
        socket.emit('create-room-error',{err:'Room already exists'});
        return;
      }

      data.members=[];
      data.membersIndex={};
      rooms[data.room]=data;
      socket.emit('create-room-ack',data);

  });

  socket.on('delete-room', (data) => {

      var room = rooms[data.room];
      if(!room) {
        socket.emit('delete-room-error',{err:'Room not exists'});
        return;
      }

      if(room.deviceId!==data.deviceId) {
        socket.emit('delete-room-error',{err:'User not authorized to delete room: '+data.room});
        return;
      }

      delete rooms[data.room];
      socket.emit('delete-room-ack',data);
  });


  socket.on('push-request', (data) => {
    data.sourceId=socket.id;  // sourceId is the user who wants to send the data

    if(data.userId && data.userId.length>0) {
        socket.to(data.userId).emit('push-request',data); // notify user that someone is wants to send him data
    } else {
        io.to(data.room).emit('push-request', data); // broadcast to everyone in the room
    }

  });

  socket.on('pull-request', (data) => {
     // only moderator can ask for other user data
     var member = findMemberBySocket(data.room, socket);
     if(member && member.isModerator) {
       data.sourceId=socket.id;  // sourceId is the user who asked for the data
       socket.to(data.userId).emit('pull-request',data); // notify user that someone is wants to pull data
     }

  });

  socket.on('join-room', (data) => {

    socket.join(data.room, () => {
      var room = rooms[data.room];
      if(!room) {
        socket.emit('join-room-error',{err:'Room not exists'});
        return;
      }

      var member = findMemberByName(data.room, data.name);
      if(member) {
        socket.emit('join-room-error',{err:'User name already exists in room'});
        return;
      }

      member = findMemberByStation(data.room, data.station);
      if(!member) {
        member = findMemberBySocket(data.room, socket);
      }

      if(!member) {

        // restore member's question
        let question = null;
        if(data.station) {
            question=room.questions[data.station];
        }

        member={name:data.name, id:socket.id, station: data.station, question: question};

        // check if user is moderator in this room
        member.isModerator=room.moderatorPwd===data.pwd;

        // regular users (non moderators) might need a password to enter the room
        if(!member.isModerator && room.pwd ) {
            if(room.pwd!==data.pwd) {
              socket.emit('join-room-error',{err:'Wrong password'});
              return;
            }
        }

          room.members.push(member);
          room.membersIndex[socket.id]=1;

          io.to(data.room).emit('user-joined', {room:data.room, name:data.name, srcStation:data.station,
                                                sourceId:socket.id, members: room.members}); // broadcast to everyone in the room
      } else {

        socket.emit('join-room-error',{err:'User already exists in room'});
        return;

      }

    });

  });

  function findMemberByName(roomName, memberName) {

      var room = rooms[roomName];
      if(!room) {
        return null;
      }

      for(var i=0;i<room.members.length;++i) {
        if(room.members[i].name===memberName) {
            return room.members[i];
        }

      }

      return null;

    }

  function findMemberByStation(roomName, station) {

    if(!station) {
        return null;
    }

    var room = rooms[roomName];
      if(!room) {
        return null;
      }

      for(var i=0;i<room.members.length;++i) {
        if(room.members[i].station===station) {
            return room.members[i];
        }

      }

      return null;

  }

  function findMemberBySocket(roomName, socket) {

    var room = rooms[roomName];
    if(!room) {
      return null;
    }

    for(var i=0;i<room.members.length;++i) {
      if(room.members[i].id===socket.id) {
          return room.members[i];
      }

    }

    return null;

  }

  function leaveRoom(roomName, socket) {

      var room = rooms[roomName];
      if(!room) {
        return false;
      }

      for(var i=0;i<room.members.length;++i) {
        if(room.members[i].id===socket.id) {
            room.members.splice(i,1);
            room.membersIndex[socket.id]=null;
            break;
        }

      }

      return true;

  }

  function getMembersWithActiveQuestion(room) {

    if(!room.members) {
      return;
    }

    var retval = [];
    for(var i=0;i<room.members.length;++i) {
      if(room.members[i].question) {
        retval.push(room.members[i]);
      }
    }

    return JSON.parse(JSON.stringify(retval));

  }

  socket.on('leave-room', (data) => {

    if(!leaveRoom(data.room, socket)) {
        socket.emit('leave-room-error',{err:'Room not exists'});
        return;
    }

    if(data.station && room.questions[data.station]) {
        room.questions[data.station]=null;
    }

    data.sourceId=socket.id;
    socket.leave(data.room, () => {

      var room = rooms[data.room];
      if(room) {
        data.members=room.members;
      }

      io.to(data.room).emit('user-left', data); // broadcast to everyone in the room
      socket.emit('user-left', data);

    });

  });

  socket.on('ask', (data)=> {

    //{room: 'room1', question:'Why do i get syntax error?', script:['Sinbad is a model;', 'Adi is a Sinbad;'] }

    data.sourceId=socket.id;
    var room = rooms[data.room];
    if(!room) {
        socket.emit('ask-error',{err:'Room not exists'});
        return;
    }

    var member = findMemberByStation(data.room, data.station);
    if(!member) {
      member = findMemberBySocket(data.room, socket);
      if(member) {
        // old way for storing questions
        member.question=data;
      } else {
        socket.emit('ask-error',{err:'User not exist in room '+data.room});
        return;
      }

    } else {
      // new way for storing questions
      member.question=data;
      room.questions[member.station]=data;

    }

    data.members = getMembersWithActiveQuestion(room); // client should get all members questions
    io.to(data.room).emit('question', data); // broadcast to everyone in the room

  });

  socket.on('review-question', (data)=> {
    data.sourceId=socket.id;
    socket.to(data.userId).emit('reviewing',data); // notify user that someone is reviewing his question
  });


  socket.on('answer-question', (data)=> {
    data.sourceId=socket.id;
    socket.to(data.userId).emit('answered',data); // notify user that someone has answered his question
  });

  socket.on('accept-answer', (data)=> {
    data.sourceId=socket.id;

    var room = rooms[data.room];
    if(!room) {
        return;
    }

    var member = findMemberByStation(data.room, data.station);
    if(!member) {
      member = findMemberBySocket(data.room, socket);
    }

    if(member) {
        member.question=null;

        if(data.station && room.questions[data.station]) {
          room.questions[data.station]=null;
        }

        data.members = getMembersWithActiveQuestion(room); // client should get all members questions
        io.to(data.room).emit('answer-accepted', data); // broadcast to everyone in the room that no need to answer this question

    }

  });


});


//process.on('uncaughtException', function (exception) {
//    console.log(new Date().toLocaleDateString() + ": "+exception);
//});

http.listen(3060, function(){
  console.log(new Date().toLocaleDateString() + ': SceneMax3D classroom server - listening on *:3060');

});
