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
writes="0"
l="5000" 
warmup="0"
snapshot="100"
writeall="0"
iterations="5" 

###############################
# Extracts values
###############################

benchs="trees.lockbased.LockRemovalSkipList trees.lockbased.DominationLockingSkipList trees.lockbased.TwoPLLockRemovalSkiplist trees.lockbased.TwoPLSkiplist trees.lockbased.LockRemovalSimple2PLSkiplist trees.lockbased.Simple2PLSkiplist trees.lockfree.LockFreeKSTRQ trees.lockfree.LockFreeJavaSkipList"
#benchs="trees.lockbased.LockRemovalSkipList trees.lockbased.DominationLockingSkipList trees.lockfree.LockFreeKSTRQ trees.lockfree.LockFreeJavaSkipList" 
ds="rangeQueries_numOfRangeQueries"

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
					in=${output}/log/${bench}-small_ranges-i${i}-u${write}-t${t}.log
                    #thavg=`grep "Throughput" ${in} | awk '{ s += $3; nb++ } END { printf "%f", s/nb }'`
					alloper=`grep "Operations:" ${in} | awk '{ s += $2;  } END { printf "%f", s }'`
					alltime=`grep "Elapsed time" ${in} | awk '{ s += $4;  } END { printf "%f", s }'`
					allrange=`grep "size successful" ${in} | awk '{ s += $3; } END { printf "%f", s }'`
                    #upavg=`grep "update" ${in} | awk '{ s += $5; nb++ } END { printf "%f", s/nb }'`
                    #printf " ${thavg} (${upavg})" >> ${out}
					printf '\t' >> ${out}
					mtmp=`echo "${allrange}/${alltime}" | bc` 
                    printf " ${mtmp}" >> ${out}					

					
		
		done

                printf '\n' >> ${out}
            done
        done
    done
	
################################################################################################

ds="rangeQueries_numOfUpdates"

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
					in=${output}/log/${bench}-small_ranges-i${i}-u${write}-t${t}.log
                    #thavg=`grep "Throughput" ${in} | awk '{ s += $3; nb++ } END { printf "%f", s/nb }'`
					alloper=`grep "Operations:" ${in} | awk '{ s += $2;  } END { printf "%f", s }'`
					allrange=`grep "size successful" ${in} | awk '{ s += $3; } END { printf "%f", s }'`
				#	allupdates=`grep "updates:" ${in} | awk '{ s += $3;  } END { printf "%f", s }'`
					alltime=`grep "Elapsed time" ${in} | awk '{ s += $4;  } END { printf "%f", s }'`
					printf '\t' >> ${out}
					allupdates=`echo "${alloper}-${allrange}" | bc`
					mtmp=`echo "${allupdates}/${alltime}" | bc` 
                    printf " ${mtmp}" >> ${out}					

					
		
		done

                printf '\n' >> ${out}
            done
        done
    done

################################################################################################
	
	
benchs="trees.transactional.SkipList"
stms="tl2"
ds="rangeQueriestran_numOfRangeQueries"

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
					in=${output}/log/${bench}-small_ranges-stm${stm}-i${i}-u${write}-t${t}.log
                     #thavg=`grep "Throughput" ${in} | awk '{ s += $3; nb++ } END { printf "%f", s/nb }'`
					alloper=`grep "Operations:" ${in} | awk '{ s += $2;  } END { printf "%f", s }'`
					alltime=`grep "Elapsed time" ${in} | awk '{ s += $4;  } END { printf "%f", s }'`
					allrange=`grep "size successful" ${in} | awk '{ s += $3; } END { printf "%f", s }'`
                    #upavg=`grep "update" ${in} | awk '{ s += $5; nb++ } END { printf "%f", s/nb }'`
                    #printf " ${thavg} (${upavg})" >> ${out}
					printf '\t' >> ${out}
					mtmp=`echo "${allrange}/${alltime}" | bc` 
                    printf " ${mtmp}" >> ${out}	
		done
		done

                printf '\n' >> ${out}
            done
        done
    done	
	
################################################################################################
	
	
benchs="trees.transactional.SkipList"
stms="tl2"
ds="rangeQueriestran_numOfUpdates"

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
					in=${output}/log/${bench}-small_ranges-stm${stm}-i${i}-u${write}-t${t}.log
                    #thavg=`grep "Throughput" ${in} | awk '{ s += $3; nb++ } END { printf "%f", s/nb }'`
					allupdates=`grep "updates:" ${in} | awk '{ s += $3;  } END { printf "%f", s }'`
					alltime=`grep "Elapsed time" ${in} | awk '{ s += $4;  } END { printf "%f", s }'`
					printf '\t' >> ${out}
					mtmp=`echo "${allupdates}/${alltime}" | bc` 
                    printf " ${mtmp}" >> ${out}					
		done
		done

                printf '\n' >> ${out}
            done
        done
    done		
