#!/usr/bin/ruby


total_score = 0

$stderr.puts
$stderr.puts
$stderr.puts "RUNNING PUBLIC TESTS...."
$stderr.puts
$stderr.puts
$stderr.puts "---------------------------------------------------"
$stderr.puts "TEST 1: COMPATIBILITY"
$stderr.puts "---------------------------------------------------"
`python3 tests/compatibility_test.py`
exit_code = ($? >> 8)
score = 0
if exit_code == 0 then
    score = 5
    total_score += 5
end
$stderr.puts "#{score}/5   Compatibility Test"
$stderr.puts
$stderr.puts

$stderr.puts "---------------------------------------------------"
$stderr.puts "TEST 2: MULTIPLE FAILURES"
$stderr.puts "---------------------------------------------------"
`python3 tests/really_big_network_multiple_failures.py`
exit_code = ($? >> 8)
score = 0
if exit_code == 0 then
    score = 15
    total_score += 15
end
$stderr.puts "#{score}/15    Routes On really big Topology After Multiple Failures"
$stderr.puts
$stderr.puts


$stderr.puts "---------------------------------------------------"
$stderr.puts "TOTAL SCORE #{total_score}/20"
$stderr.puts "---------------------------------------------------"
