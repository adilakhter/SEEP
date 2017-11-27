load sprintf("%s/colours.plt",tmpldir)
set terminal term 
set output sprintf("%s/%s/tput_vs_mobility_stddev%s",outputdir,timestr,termext)

set xlabel "Node speed (m/s)" font ", 20" 
set ylabel "Throughput (Kb/s)" font ", 20" offset -2
#set xrange [0.9:2.6]
#set xtics autofreq 0.5
set xrange [0:16]
set xtics autofreq 5
#set xrange [0.9:3.1]
#set xtics autofreq 0.5
#set ytics autofreq 50
set tics font ", 16"
set xtics nomirror
set ytics nomirror

set border linewidth 1.5
set style line 1 linewidth 2.5 linecolor rgb "red"
set style line 2 linewidth 2.5 linecolor rgb "blue"
set style line 3 linewidth 2.5 linecolor rgb "green"
set style line 4 linewidth 2.5 linecolor rgb "pink"
set style line 5 linewidth 2.5 linecolor rgb "violet"

#set boxwidth 0.1
#set style fill empty 
set key spacing 1.75 font ", 16"
#set key bottom right
#set bmargin 4
#set lmargin 13 

plot sprintf("%s/%s/1k-tput.data",outputdir,timestr) using 1:2 title "r=1" w lines linestyle 1, \
	sprintf("%s/%s/2k-tput.data",outputdir,timestr) using 1:2 title "r=2" w lines linestyle 2, \
	sprintf("%s/%s/3k-tput.data",outputdir,timestr) using 1:2 title "r=3" w lines linestyle 3, \
	sprintf("%s/%s/5k-tput.data",outputdir,timestr) using 1:2 title "r=5" w lines linestyle 4, \
	sprintf("%s/%s/1k-tput.data",outputdir,timestr) using 1:2:4 notitle linestyle 1 with yerrorb, \
	sprintf("%s/%s/2k-tput.data",outputdir,timestr) using ($1+0.02):2:4 notitle linestyle 2 with yerrorb, \
	sprintf("%s/%s/3k-tput.data",outputdir,timestr) using ($1+0.04):2:4 notitle linestyle 3 with yerrorb, \
	sprintf("%s/%s/5k-tput.data",outputdir,timestr) using ($1+0.06):2:4 notitle linestyle 4 with yerrorb
	#sprintf("%s/%s/2k-tput.data",outputdir,timestr) using ($1+0.05):2:4 notitle linestyle 2 with yerrorb, \
	#sprintf("%s/%s/3k-tput.data",outputdir,timestr) using ($1+0.10):2:4 notitle linestyle 3 with yerrorb, \
	#sprintf("%s/%s/5k-tput.data",outputdir,timestr) using ($1+0.15):2:4 notitle linestyle 4 with yerrorb
