#!/usr/bin/python

import subprocess,os,time,re,argparse,sys

from compute_stats import compute_stats

script_dir = os.path.dirname(os.path.realpath(__file__))

# alg =1, k=2, tput=3, framerate=4, power=5
#exp_compute_coords = { 'pi-tput-vs-rr' : (1,3), 
#                        'pi-tput-scaling' : (2,3), 
#                        'pi-power-scaling' : (2,5)}

exp_compute_coords = {'pi-tput-vs-rr' : [1,3], 
                        'pi-tput-vs-rr-dense' : [1,3], 
                        'pi-tput-scaling' : [2,3], 
                        'pi-tput-scaling-dense' : [2,3], 
                        'pi-power-scaling' : [2,7], 
                        'pi-power-scaling-excl-base' : [2,6],
                        'pi-power-scaling-active-only' : [2,8], 
                        'pi-power-scaling-excl-base-breakdown' : [2,9,10,11], 
                        'pi-power-efficiency-scaling' : [2,7], 
                        'pi-power-efficiency-scaling-excl-base' : [2,6],
                        'pi-power-efficiency-scaling-active-only' : [2,8], 
                        'pi-power-efficiency-scaling-excl-base-breakdown' : [2,9,10,11] } 
#                        'pi-power-efficiency-scaling' : [2,7], 
#                        'pi-power-efficiency-scaling-excl-base' : [2,6],
#                        'pi-power-efficiency-scaling-active-only' : [2,8], 
#                        'pi-power-efficiency-scaling-excl-base-breakdown' : [2,9,10,11] } 

exp_results_files = { 'pi-power-scaling-excl-base-breakdown' : ['results-net.txt', 
                                                        'results-cpu.txt', 
                                                        'results-ops.txt'], 
                        'pi-power-efficiency-scaling-excl-base-breakdown' : ['results-net.txt', 
                                                        'results-cpu.txt', 
                                                        'results-ops.txt'] } 

exp_compute_numerator = { 'pi-power-efficiency-scaling' : 3, 
                        'pi-power-efficiency-scaling-excl-base' : 3,
                        'pi-power-efficiency-scaling-active-only' : 3, 
                        'pi-power-efficiency-scaling-excl-base-breakdown' : 3 } 

#exp_compute_divisor = { 'pi-power-efficiency-scaling' : 2, 
#                        'pi-power-efficiency-scaling-excl-base' : 2,
#                        'pi-power-efficiency-scaling-active-only' : 2, 
#                        'pi-power-efficiency-scaling-excl-base-breakdown' : 2 } 

#pi_combined = ['pi-tput-scaling-all', 'pi-tput-vs-rr-all'] 
pi_combined = ['pi-tput-scaling-all'] 

def main(time_strs, exp_name, cross_dir): 

    #time_str = 'ft_results'

    data_dir = '%s/log'%script_dir
    #plot('pi_tput', time_str, script_dir, data_dir)
    #if len(time_strs) > 1: # Cross plot
    if not time_strs: # Cross plot
        results_dir = '%s/%s'%(data_dir, cross_dir)

        """
        # Gen raw exps.txt
        for time_str in time_strs:
            exp_data = get_exp_data(time_str, data_dir)
            write_raw_result_line(exp_data, '%s/%s/exps.txt'%(results_dir, exp))
        """

        # Create aggregated plot data 
        for exp in exp_compute_coords.keys(): 
            # Get the raw results for each run of this experiment 
            raw_results = get_raw_result_lines('%s/%s/exps.txt'%(results_dir, exp))

            # Compute the aggregate results across all runs
            exp_results = compute_exp_results(exp, raw_results)

            for i, exp_result in enumerate(exp_results):
                results_file = exp_results_files[exp][i] if exp in exp_results_files else 'results.txt'
                write_exp_results(exp, exp_result, results_dir, results_file) # Record aggregated results

            plot(exp, '%s/%s'%(cross_dir, exp), script_dir, data_dir, add_to_envstr=';expname=\'%s\''%'fr')
        
        for exp in pi_combined:
            plot(exp, '%s/%s'%(cross_dir, exp), script_dir, data_dir, add_to_envstr=';expname=\'%s\''%'fr')

    elif exp_name:
        plot('pi_tput', time_str, script_dir, data_dir, add_to_envstr=';expname=\'%s\''%exp_name)
    else:
        for time_str in time_strs:
            plot('pi_op_cum_tput_fixed_kvarsession', time_str, script_dir, data_dir)
            plot('pi_op_tput_fixed_kvarsession', time_str, script_dir, data_dir)
            plot('pi_op_cum_util_fixed_kvarsession', time_str, script_dir, data_dir)
            plot('pi_op_weight_info_fixed_kvarsession', time_str, script_dir, data_dir)

"""
def get_exp_data(time_str, data_dir):
    alg = read_exp_alg(time_str, data_dir)
    k = read_exp_replication_factor(time_str, data_dir)
    tput = read_exp_tput(time_str, data_dir)
    frame_rate = read_exp_frame_rate(time_str, data_dir)
    total_power = read_exp_total_power(time_str, data_dir)
    total_excl_base_power = read_total_excl_base_power(time_str, data_dir)
    total_network_power = read_total_network_power(time_str, data_dir)
    total_cpu_power = read_total_cpu_power(time_str, data_dir)
"""

def plot(p, time_str, script_dir, data_dir, term='pdf', add_to_envstr=''):
    exp_dir = '%s/%s'%(data_dir,time_str)
    tmpl_dir = '%s/vldb/config'%script_dir
    tmpl_file = '%s/%s.plt'%(tmpl_dir,p)

    if term == 'pdf': 
        term_ext = '.pdf'
    elif term == 'latex' or term == 'epslatex': 
        term_ext = '.tex'
    else: raise Exception('Unknown gnuplot terminal type: '+term)

    envstr = 'timestr=\'%s\';outputdir=\'%s\';tmpldir=\'%s\';term=\'%s\';termext=\'%s\''%(time_str,data_dir,tmpl_dir, term, term_ext)
    envstr += add_to_envstr 

    plot_proc = subprocess.Popen(['gnuplot', '-e', envstr, tmpl_file], cwd=exp_dir)
    plot_proc.wait()

def get_raw_result_lines(raw_exps_file):
    with open(raw_exps_file, 'r') as raw:
        # Read and parse relevant data
        lines = (line.rstrip() for line in raw)
        lines = list(line for line in lines if line and not line.startswith('#'))
        return lines

## Helper functions to compute experiment results
def compute_exp_results(exp, raw_results):
    exp_results = []
    x = exp_compute_coords[exp][0]
    for y in exp_compute_coords[exp][1:]:
        exp_results.append(compute_xy_exp_results(x, y, raw_results, exp))

    #y = exp_compute_coords[exp][1]
    #return compute_xy_exp_results(x, y, raw_results)
    return exp_results

def compute_xy_exp_results(x, y, raw_results, exp):
    exp_results = {}
    for line in raw_results:
        els = line.split(' ')
        y_val = float(els[y])
        if exp in exp_compute_numerator:
            y_val = (float(els[exp_compute_numerator[exp]])) / y_val 
            #y_val = y_val / (float(els[exp_compute_divisor[exp]])/1024.0)
        ##TODO: Geometric mean?
        exp_results[els[x]] = exp_results.get(els[x], []) + [y_val]

    for er in exp_results:
        exp_results[er] = compute_stats(map(float, exp_results[er]))

    return exp_results

## Helper function to record experiment results
def write_exp_results(exp, exp_results, results_dir, results_file='results.txt'):
    with open('%s/%s/%s'%(results_dir, exp, results_file), 'w') as rf:
        for exp in reversed(sorted(exp_results.keys())):
        #for exp in sorted(exp_results.keys()):
            rf.write('%s %s\n'%(exp, " ".join('{:.1f}'.format(x) for x in exp_results[exp])))

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Plot ft experiments')
    #parser.add_argument('--timeStr', dest='time_str', help='log dir containing exp results')
    parser.add_argument('--timeStrs', default=None, dest='time_strs', help='log dirs containing exp results')
    parser.add_argument('--crossDir', default='pi_results', dest='cross_dir', help='log dir to store aggregate results ')
    parser.add_argument('--expName', default=None, dest='exp_name', help='plot aggregate results with this name')
    args=parser.parse_args()

    timeStrs = args.time_strs.split(',') if args.time_strs else []
    #main(args.time_str, args.exp_name)
    main(timeStrs, args.exp_name, args.cross_dir)

