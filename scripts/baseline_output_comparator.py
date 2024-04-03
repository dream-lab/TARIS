import argparse
import os
import pickle


def create_gradoop_index(results_dir):
    """Create an index for Gradoop results.

    :param results_dir: Path to the output files
    """
    index_file_path = f'../index/{graph_name}/{algorithm}_gradoop_index.pickle'
    index, l_read_ts = pickle.load(open(index_file_path, 'rb')) if os.path.exists(
        index_file_path) and not index_recreate else (dict(), set())

    for r_dir in sorted(os.listdir(results_dir)):
        if r_dir in l_read_ts:
            continue

        ts_dir = os.path.join(results_dir, r_dir, 'vertices.csv')
        if not os.path.isdir(ts_dir):
            continue

        ts = int(r_dir)
        l_read_ts.add(ts)

        for worker in os.listdir(ts_dir):
            worker_file_path = os.path.join(ts_dir, worker)

            with (open(worker_file_path, 'r') as file):
                for line in file:
                    cells = line.split(';')
                    v_id, result = int(cells[0], 16), cells[3].split('|')[0]
                    result = None if result in {"9223372036854775807"} else result.split('.')[0]
                    if algorithm not in {'Reachability', 'TMST'} and result is not None:
                        result = int(result.split('.')[0])
                        if result == 2147483647:
                            result = None
                    try:
                        index_key = (int(v_id), result)
                        index[index_key].add(ts)
                    except KeyError:
                        index[index_key] = set()
                        index[index_key].add(ts)

    pickle.dump((index, l_read_ts), open(index_file_path, 'wb'))
    return index, l_read_ts


def create_wicm_index(results_file_path):
    index = dict()
    index_file_path = f'../index/{graph_name}/{algorithm}_iwicm_index.pickle'
    if os.path.exists(index_file_path):
        print('Loading index from pickle file : ', index_file_path)
        index = pickle.load(open(index_file_path, 'rb'))
    else:
        with open(results_file_path, 'r') as results_file:
            for line in results_file:
                # It's of type : 5	[85,90)	2147483647	[90,99)	90
                cells = line.strip().split('\t')
                v_id = int(cells[0])

                if algorithm == 'FAST':
                    # FAST has only one result
                    wicm_result = cells[1].strip('\n')
                    wicm_result = -1 if wicm_result in {"2147483647"} else int(float(wicm_result))
                    index_key = (v_id, wicm_result)
                    index[index_key] = {365}
                    continue

                wicm_result = 0
                for i in range(1, len(cells), 2):
                    start, end = map(int, cells[i].strip('[').strip(')').strip('\n').split(','))
                    wicm_result = cells[i + 1].strip('').strip('\n')

                    if algorithm not in {'Reachability', 'TMST'}:
                        # convert double like 1.0 to int 1
                        wicm_result = None if wicm_result in {"Infinity", "2147483647"} else int(float(wicm_result))

                    if wicm_result == '(-1,2147483647)':
                        wicm_result = None

                    timesteps = set(range(start, end))
                    index_key = (v_id, wicm_result)
                    try:
                        for ts in timesteps:
                            index[index_key].add(ts)
                    except KeyError:
                        index[index_key] = timesteps
                index_key = (v_id, wicm_result)
                for ts in range(end, graph_len + 1):
                    index[index_key].add(ts)
    pickle.dump(index, open(index_file_path, 'wb'))
    return index


def create_tink_index(results_dir):
    """Create an index for Tink results.

    :param results_dir: Path to the output files
    """
    index_file_path = f'../index/{graph_name}/{algorithm}_tink_index.pickle'
    index, l_read_ts = pickle.load(open(index_file_path, 'rb')) if os.path.exists(
        index_file_path) and not index_recreate else (dict(), set())

    for r_dir in sorted(os.listdir(results_dir)):
        if r_dir in l_read_ts:
            continue

        ts_dir = os.path.join(results_dir, r_dir)
        if not os.path.isdir(ts_dir):
            continue

        ts = int(r_dir)
        l_read_ts.add(ts)

        for worker in os.listdir(ts_dir):
            worker_file_path = os.path.join(ts_dir, worker)

            with (open(worker_file_path, 'r') as file):
                for line in file:
                    if algorithm == 'TMST':
                        tink_v_id, tink_ans_id, tink_ans = line.strip('(').strip(')\n').split(',')
                        tink_ans = f'({tink_ans_id.strip("(")},{tink_ans})'
                        if tink_ans == '(-1,9223372036854775807)':
                            tink_ans = None
                    else:
                        tink_v_id, tink_ans = line.strip('(').strip(')\n').split(',')
                        if algorithm != 'Reachability':
                            tink_ans = None if tink_ans in {"9.223372036854776E18", "2147483647"} else int(tink_ans.split('.')[0])
                    try:
                        index_key = (int(tink_v_id), tink_ans)
                        index[index_key].add(ts)
                    except KeyError:
                        index[index_key] = set()
                        index[index_key].add(ts)
    pickle.dump((index, l_read_ts), open(index_file_path, 'wb'))
    return index, l_read_ts


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description="Compares the results of iWicm with Tink and Gradoop")
    parser.add_argument('-g', '--graph', type=str, help='Name of the graph', required=True)
    parser.add_argument('-a', '--algorithm', type=str, help='Name of the algorithm', required=True)
    parser.add_argument('-f', '--framework', type=str, help='Name of the framework', required=True)
    parser.add_argument('-t', '--taris_output_dir', type=bool, help='TARIS output directory', required=True, default=False)
    parser.add_argument('-o', '--framework_output_dir', type=bool, help='Framework output directory', required=True, default=False)
    parser.add_argument('-i', '--index_recreate', type=bool, help='Recreate the index', required=False, default=False)
    args = parser.parse_args()

    graph_name = args.graph
    algorithm = args.algorithm
    comparing_framework = args.framework
    index_recreate = args.index_recreate
    taris_output_dir = args.taris_output_dir
    framework_output_dir = args.framework_output_dir
    graph_len = 122 if graph_name == 'Reddit' else 365 if graph_name == 'LDBC' else 40

    # input files
    compared_to_dir_path = framework_output_dir
    config = dict()
    config[comparing_framework] = f'{compared_to_dir_path}/results'
    config['wicm'] = f'{taris_output_dir}/sorted.txt'
    config['match'] = f'{compared_to_dir_path}/match_w.r.t_wicm.csv'
    config['mismatch'] = f'{compared_to_dir_path}/mismatch_w.r.t_wicm.csv'

    # process
    print(f'Processing iWicm Results: {config["wicm"]}')
    iwicm_index = create_wicm_index(config['wicm'])
    print(f'Processing {comparing_framework} Results: {config[comparing_framework]}')
    comparing_index, read_ts = create_tink_index(
        config[comparing_framework]) if comparing_framework == 'tink' else create_gradoop_index(
        config[comparing_framework])

    # compare and report
    print('Comparing Results')
    # open both output and mismatch files
    with open(config['match'], 'w') as match, open(config['mismatch'], 'w') as mismatch:
        match.write('VertexId\tResult\tTink\tiWICM\n')
        mismatch.write('VertexId\tResult\tTink\tiWICM\n')
        total = 0
        mismatch_count = 0
        key_error_count = 0
        for key, val in comparing_index.items():
            total += 1
            try:
                iwicm_timesteps = iwicm_index[(key[0], key[1])]
                iwicm_timesteps = iwicm_timesteps.intersection(read_ts)
                if iwicm_timesteps == val:
                    match.write(f'{key[0]}\t{key[1]}\t{val}\t{iwicm_timesteps}\n')
                else:
                    mismatch_count += 1
                    mismatch.write(f'{key[0]}\t{key[1]}\t{val}\t{iwicm_timesteps}\n')
            except KeyError:
                key_error_count += 1
                mismatch.write(f'{key[0]}\t{key[1]}\t{val}\tKeyError\n')
        print(f'KeyError: {key_error_count * 100 / total}%\tMismatch: {mismatch_count * 100 / total}%')
        print('Output files are at : ', config['match'], config['mismatch'])
        print('Done')