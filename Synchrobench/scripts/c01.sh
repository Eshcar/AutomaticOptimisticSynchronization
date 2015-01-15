#!/bin/bash

dir=..
output=${dir}/output
deuce="${dir}/lib/mydeuce.jar"
agent=${dir}/lib/deuceAgent-1.3.0.jar
bin=${dir}/bin
java=java
javaopt=-server
thread="1 2 4 8 16 32"
size="10000 1000000"
writes="100 50 0"
l="5000" 
warmup="0"
snapshot="0"
writeall="0"
iterations="5" 


###############################
# Extracts values
###############################

ds="trees"
benchs="trees.lockbased.LockRemovalTree trees.lockbased.LogicalOrderingAVL trees.lockbased.LockRemovalTreap trees.lockbased.LockBasedStanfordTreeMap trees.lockbased.LockBasedFriendlyTreeMap trees.lockbased.DominationLockingTree trees.lockbased.DominationLockingTreap trees.lockfree.NonBlockingTorontoBSTMap trees.lockfree.LockFreeJavaSkipList trees.lockfree.LockFreeJavaSkipList trees.lockbased.LogicalOrderingTree"
# write header
    for write in ${writes}; do
        for i in ${size}; do
            r=`echo "2*${i}" | bc`
            out=${output}/data/${ds}-i${i}-u${write}.log
	    printf "#" > ${out}
	    for bench in ${benchs}; do
		      printf '\t' >> ${out}
              printf " ${bench}" >> ${out}
            done
            printf '\n' >> ${out}
	done
    done
# write average
    for write in ${writes}; do
        for i in ${size}; do
            r=`echo "2*${i}" | bc`
            out=${output}/data/${ds}-i${i}-u${write}.log
            for t in ${thread}; do
                printf $t >> ${out}
		for bench in ${benchs}; do 
                    in=${output}/log/${bench}-abcdefg-i${i}-u${write}-t${t}.log
                    thavg=`grep "Throughput" ${in} | awk '{ s += $3; nb++ } END { printf "%f", s/nb }'`
                    #upavg=`grep "update" ${in} | awk '{ s += $5; nb++ } END { printf "%f", s/nb }'`
                    #printf " ${thavg} (${upavg})" >> ${out}
					printf '\t' >> ${out}
                    printf " ${thavg}" >> ${out}					
		done

                printf '\n' >> ${out}
            done
        done
    done

#############################################################################################

benchs="trees.transactional.BinaryTree trees.transactional.Treap"
stms="tl2"
ds="tran"

# write header
    for write in ${writes}; do
        for i in ${size}; do
            r=`echo "2*${i}" | bc`
            out=${output}/data/${ds}-i${i}-u${write}.log
	    printf "#" > ${out}
		for stm in ${stms}; do
	    for bench in ${benchs}; do
		      printf '\t' >> ${out}
              printf " ${bench}" >> ${out}
        done
		done
        printf '\n' >> ${out}
	done
    done
# write average
    for write in ${writes}; do
        for i in ${size}; do
            r=`echo "2*${i}" | bc`
            out=${output}/data/${ds}-i${i}-u${write}.log
            for t in ${thread}; do
                printf $t >> ${out}
		for stm in ${stms}; do  
		for bench in ${benchs}; do                     
					in=${output}/log/${bench}-stm${stm}-i${i}-u${write}-t${t}.log
                    thavg=`grep "Throughput" ${in} | awk '{ s += $3; nb++ } END { printf "%f", s/nb }'`
                    #upavg=`grep "update" ${in} | awk '{ s += $5; nb++ } END { printf "%f", s/nb }'`
                    #printf " ${thavg} (${upavg})" >> ${out}
					printf '\t' >> ${out}
                    printf " ${thavg}" >> ${out}					
		done
		done

                printf '\n' >> ${out}
            done
        done
    done
