#!/usr/bin/ruby
total_score = 0

$stderr.puts "---------------------------------------------------"
$stderr.puts "TEST A1: Add or delete DNS records"
$stderr.puts "---------------------------------------------------"
`python3 tests/a1_add_or_del_dns_records.py`
exit_code = ($? >> 8)
score = 0
if exit_code == 0 then
    score = 10
    total_score += 10
end
$stderr.puts "#{score}/10    DNS records add / delete"
$stderr.puts
$stderr.puts


$stderr.puts "---------------------------------------------------"
$stderr.puts "TEST A3: Check DNS format"
$stderr.puts "---------------------------------------------------"
`python3 tests/a3_check_dns_format.py`
exit_code = ($? >> 8)
score = 0
if exit_code == 0 then
    score = 10
    total_score += 10
end
$stderr.puts "#{score}/10    DNS format has been checked"
$stderr.puts
$stderr.puts
