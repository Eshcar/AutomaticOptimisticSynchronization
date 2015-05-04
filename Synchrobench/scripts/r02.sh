#!/bin/bash

dir=..
output=${dir}/output
deuce="${dir}/lib/mydeuce.jar"
agent=${dir}/lib/deuceAgent-1.3.0.jar
bin=${dir}/bin
java=java
javaopt=-server

thread="1 2 4 8 16 32"
size="1000000"
writes="50"
l="5000" 
warmup="0"
snapshot="50"
writeall="0"
iterations="10" 

CP=${dir}/lib/compositional-deucestm-0.1.jar:${dir}/lib/mydeuce.jar:${dir}/bin
MAINCLASS=contention.benchmark.Test

if [ ! -d "${output}" ]; then
  mkdir $output
else
  rm -rf ${output}/*
fi

mkdir ${output}/log ${output}/data ${output}/plot ${output}/ps

###############################
# records all benchmark outputs
###############################

# benchmarks
benchs="trees.lockbased.LockRemovalSkipList trees.lockbased.DominationLockingSkipList trees.lockbased.TwoPLLockRemovalSkiplist trees.lockbased.TwoPLSkiplist trees.lockbased.LockRemovalSimple2PLSkiplist trees.lockbased.Simple2PLSkiplist trees.lockfree.LockFreeKSTRQ trees.lockfree.LockFreeJavaSkipList" 
 
 for bench in ${benchs}; do
   for write in ${writes}; do
    for t in ${thread}; do
     for i in ${size}; do
       r=`echo "2*${i}" | bc`
       out=${output}/log/${bench}-small_ranges-i${i}-u${write}-t${t}.log
       for (( j=1; j<=${iterations}; j++ )); do
	   ${java} ${javaopt} -cp ${CP} ${MAINCLASS} -W ${warmup} -u ${write} -s ${snapshot} -d ${l} -t ${t} -i ${i} -r ${r} -R true -b ${bench} 2>&1 >> ${out}
       done
     done
    done
   done
 done

#############################################################################################

# transaction-based benchmarks
benchs="trees.transactional.SkipList"
stms="tl2"
JVMARGS=-Dorg.deuce.exclude="java.util.*,java.lang.*,sun.*" 
BOOTARGS=-Xbootclasspath/p:${dir}/lib/rt_instrumented.jar
CP=${dir}/lib/compositional-deucestm-0.1.jar:${dir}/lib/mydeuce.jar:${dir}/bin

 for bench in ${benchs}; do
  for write in ${writes}; do
   for t in ${thread}; do
     for i in ${size}; do
	   r=`echo "2*${i}" | bc`
       for stm in ${stms}; do
	       out=${output}/log/${bench}-small_ranges-stm${stm}-i${i}-u${write}-t${t}.log
         for (( j=1; j<=${iterations}; j++ )); do
	     echo "${java} ${javaopt} ${JVMARGS} -Dorg.deuce.transaction.contextClass=org.deuce.transaction.${stm}.Context -javaagent:${agent} -cp ${CP} ${BOOTARGS} ${MAINCLASS} -W ${warmup} -u ${write} -a ${writeall} -s ${snapshot} -l ${l} -t ${t} -i ${i} -r ${r} -b ${BENCHPATH}.${bench} -v"
	     ${java} ${javaopt} ${JVMARGS} -Dorg.deuce.transaction.contextClass=org.deuce.transaction.${stm}.Context -javaagent:${agent} -cp ${CP} ${BOOTARGS} ${MAINCLASS} -W ${warmup} -u ${write} -a ${writeall} -s ${snapshot} -d ${l} -t ${t} -i ${i} -r ${r} -R true -b ${bench} -v 2>&1 >> ${out}
	 done
       done
     done
   done
  done
 done

