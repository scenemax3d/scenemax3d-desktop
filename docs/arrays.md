# Arrays

*Available since version 1.7.4*

## Defining Arrays

```scenemax
var arr=[10,20,30,40,50,60,70]
```

## Object Arrays

Create an array of different objects and access one randomly:

```scenemax
s1 is a sinbad
s2 is a ninja
s3 is a bird sprite
b is a box

var my_objects = [s1,s2,s3,b]
var random_obj = my_objects [rnd(4)]
random_obj.turn left 360 in 10 seconds
```

## Nested Arrays

Arrays can contain other arrays:

```scenemax
var arr1=[10,20,30,40,50]
var arr2 = [10,20,30,40,50,60,70]
var arr3 = [1,2,3,4,5,6,"hello world"]
var my_arrays = [arr1,arr2,arr3]
```
